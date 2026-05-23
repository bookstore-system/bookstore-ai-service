package com.notfound.aiservice.agent.impl;

import com.notfound.aiservice.agent.AiAgentService;
import com.notfound.aiservice.agent.BookCardExtractor;
import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolRegistry;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.agent.tool.impl.GuardrailTool;
import com.notfound.aiservice.model.dto.request.AgentChatRequest;
import com.notfound.aiservice.model.dto.request.AttachmentRequest;
import com.notfound.aiservice.model.dto.response.AgentChatResponse;
import com.notfound.aiservice.service.impl.GeminiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator chính của AI Agent.
 *
 * Luồng xử lý:
 *  1. Pre-flight guardrail (CS-15): từ chối ngay nếu out-of-scope rõ ràng.
 *  2. Build prompt + tool schema gửi cho Gemini (function calling).
 *  3. Vòng lặp tool-call:
 *     - Nếu Gemini trả về functionCall → thực thi tool tương ứng → đẩy
 *       functionResponse lại cho Gemini → lặp tiếp.
 *     - Nếu Gemini trả về text → kết thúc, đó là câu trả lời cuối.
 *  4. Trả kèm trace (các tool đã dùng + arg + data) cho FE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl implements AiAgentService {

    private final ToolRegistry toolRegistry;
    private final GeminiClientService geminiClientService;
    private final GuardrailTool guardrailTool;
    private final BookCardExtractor bookCardExtractor;

    @Value("${ai.agent.max-tool-iterations:4}")
    private int maxToolIterations;

    private final Map<String, List<Map<String, Object>>> historyBySession = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_TURNS = 10;

    private static final String SYSTEM_PROMPT = """
            Bạn là BookBot - trợ lý ảo của Nhà sách online.
            Nhiệm vụ:
              1. Hiểu intent của user (tìm sách, gợi ý, so sánh, review, đơn hàng, ...).
              2. Tự quyết định dùng tool nào trong danh sách có sẵn.
              3. Gọi tool, đọc kết quả rồi tổng hợp câu trả lời tiếng Việt thân thiện.
              4. Không bịa thông tin - luôn dùng dữ liệu tool trả về.
              5. Nếu câu hỏi ngoài phạm vi nhà sách, từ chối lịch sự và gợi ý tìm sách liên quan.

            Bộ tool có sẵn:
              - searchBooksTool   : tìm sách theo keyword
              - semanticSearchTool: tìm sách theo ngữ nghĩa/cảm xúc
              - recommendationTool: gợi ý sách cá nhân hóa
              - compareBooksTool  : so sánh nhiều cuốn
              - reviewSummaryTool : lấy review để tóm tắt sentiment
              - stockCheckTool    : kiểm tra tồn kho / lọc theo giá-thể loại
              - promotionTool     : gợi ý mã giảm giá theo giá trị đơn hàng
              - imageScannerTool  : nhận diện sách qua ảnh bìa (multimodal)
              - orderLookupTool   : tra cứu đơn hàng của user
              - guardrailTool     : kiểm tra câu hỏi ngoài phạm vi (chỉ gọi khi nghi ngờ)

            Quy tắc trả lời:
              - Khi đã có dữ liệu từ tool, viết phản hồi tự nhiên bằng tiếng Việt.
              - GIAO DIỆN sẽ hiển thị card sách (ảnh bìa, tên, giá, rating, nút "Xem chi tiết")
                ngay phía dưới lời chào của bạn, do field "books" trong response sinh ra.
              - VÌ VẬY, KHÔNG liệt kê dài dòng danh sách sách trong phần text:
                  + Tóm tắt ngắn 1-2 câu (vd: "Mình tìm được 5 cuốn phù hợp, bạn xem các card bên dưới nhé.").
                  + Có thể nhấn 1-2 cuốn nổi bật, kèm 1 câu lý do.
                  + Không lặp lại giá / rating / link / id - card đã có sẵn các thông tin đó.
              - Với câu hỏi không tìm/gợi ý sách (review, đơn hàng, mã giảm giá, OOC...):
                trả lời bình thường, không bắt buộc nhắc tới card.
            """;

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        String sessionId = (request.getSessionId() == null || request.getSessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getSessionId();

        ToolContext context = ToolContext.builder()
                .sessionId(sessionId)
                .userId(request.getUserId())
                .userMessage(request.getMessage())
                .attachments(request.getAttachments())
                .build();

        // 1. Pre-flight guardrail
        Map<String, Object> guard = guardrailTool.classify(request.getMessage());
        if ("OUT_OF_SCOPE".equals(guard.get("decision"))) {
            String suggested = (String) guard.get("suggestedResponse");
            return AgentChatResponse.builder()
                    .sessionId(sessionId)
                    .intent("OUT_OF_SCOPE")
                    .response(suggested)
                    .toolCalls(List.of(
                            AgentChatResponse.ToolCallTrace.builder()
                                    .toolName(GuardrailTool.NAME)
                                    .arguments(Map.of("message", request.getMessage()))
                                    .success(true)
                                    .data(guard)
                                    .build()
                    ))
                    .books(List.of())
                    .build();
        }

        if (!geminiClientService.isConfigured()) {
            return AgentChatResponse.builder()
                    .sessionId(sessionId)
                    .intent("CONFIG_ERROR")
                    .response("Gemini API key chưa được cấu hình. Vui lòng set GEMINI_API_KEY.")
                    .toolCalls(List.of())
                    .books(List.of())
                    .build();
        }

        // 2. Build contents
        List<Map<String, Object>> contents = buildInitialContents(sessionId, request);

        Collection<ToolSchema> schemas = toolRegistry.getAll().stream()
                .map(Tool::getSchema)
                .toList();

        List<AgentChatResponse.ToolCallTrace> trace = new ArrayList<>();
        String finalAnswer = null;
        String detectedIntent = null;

        // 3. Vòng lặp tool-call
        for (int iter = 0; iter < maxToolIterations; iter++) {
            Map<String, Object> response = geminiClientService.generateWithTools(contents, schemas);
            if (response.containsKey("error")) {
                finalAnswer = "AI service lỗi: " + response.get("error");
                break;
            }

            ParsedReply reply = parseReply(response);

            if (reply.functionCallName != null) {
                if (detectedIntent == null) detectedIntent = reply.functionCallName;

                Tool tool = toolRegistry.get(reply.functionCallName).orElse(null);
                ToolResult result;
                if (tool == null) {
                    result = ToolResult.fail(reply.functionCallName, "Tool không tồn tại");
                } else {
                    try {
                        result = tool.execute(reply.functionCallArgs, context);
                    } catch (Exception e) {
                        log.warn("Tool {} failed", reply.functionCallName, e);
                        result = ToolResult.fail(reply.functionCallName, e.getMessage());
                    }
                }

                trace.add(AgentChatResponse.ToolCallTrace.builder()
                        .toolName(reply.functionCallName)
                        .arguments(reply.functionCallArgs)
                        .success(result.isSuccess())
                        .errorMessage(result.getErrorMessage())
                        .data(result.getData())
                        .build());

                contents.add(modelFunctionCallPart(reply.functionCallName, reply.functionCallArgs));
                contents.add(userFunctionResponsePart(reply.functionCallName, result));
                continue;
            }

            if (reply.text != null && !reply.text.isBlank()) {
                finalAnswer = reply.text;
                break;
            }

            log.warn("Gemini returned neither text nor function call. Stopping loop.");
            break;
        }

        if (finalAnswer == null) {
            finalAnswer = "Mình đã dùng quá nhiều bước nhưng chưa có câu trả lời, bạn thử hỏi rõ hơn nhé.";
        }

        updateHistory(sessionId, request.getMessage(), finalAnswer);

        List<com.notfound.aiservice.model.dto.response.BookCard> books = bookCardExtractor.extract(trace);

        return AgentChatResponse.builder()
                .sessionId(sessionId)
                .intent(detectedIntent == null ? "DIRECT_ANSWER" : detectedIntent)
                .response(finalAnswer)
                .toolCalls(trace)
                .books(books)
                .build();
    }

    private List<Map<String, Object>> buildInitialContents(String sessionId, AgentChatRequest request) {
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", SYSTEM_PROMPT))
        ));
        contents.add(Map.of(
                "role", "model",
                "parts", List.of(Map.of("text", "Đã hiểu. Mình sẽ tuân thủ và chỉ tư vấn trong phạm vi nhà sách."))
        ));

        List<Map<String, Object>> history = historyBySession.getOrDefault(sessionId, List.of());
        contents.addAll(history);

        List<Map<String, Object>> userParts = new ArrayList<>();
        userParts.add(Map.of("text", request.getMessage()));

        if (request.getAttachments() != null) {
            for (AttachmentRequest a : request.getAttachments()) {
                if (a == null) continue;
                if (a.getType() != null && a.getType().toLowerCase().startsWith("image")) {
                    userParts.add(Map.of("text",
                            "[user kèm ảnh: type=" + a.getType()
                                    + (a.getName() != null ? ", name=" + a.getName() : "")
                                    + ". Nếu cần đọc ảnh, hãy gọi imageScannerTool.]"));
                } else {
                    userParts.add(Map.of("text",
                            "[attachment type=" + a.getType()
                                    + (a.getName() != null ? ", name=" + a.getName() : "")
                                    + (a.getUrl() != null ? ", url=" + a.getUrl() : "") + "]"));
                }
            }
        }

        contents.add(Map.of("role", "user", "parts", userParts));
        return contents;
    }

    private Map<String, Object> modelFunctionCallPart(String name, Map<String, Object> args) {
        Map<String, Object> functionCall = new LinkedHashMap<>();
        functionCall.put("name", name);
        functionCall.put("args", args == null ? Map.of() : args);
        return Map.of(
                "role", "model",
                "parts", List.of(Map.of("functionCall", functionCall))
        );
    }

    private Map<String, Object> userFunctionResponsePart(String name, ToolResult result) {
        Map<String, Object> responseMap = new LinkedHashMap<>();
        if (result.isSuccess()) {
            responseMap.put("success", true);
            responseMap.put("data", result.getData() == null ? Map.of() : result.getData());
        } else {
            responseMap.put("success", false);
            responseMap.put("error", result.getErrorMessage());
        }
        Map<String, Object> functionResponse = new LinkedHashMap<>();
        functionResponse.put("name", name);
        functionResponse.put("response", responseMap);
        return Map.of(
                "role", "user",
                "parts", List.of(Map.of("functionResponse", functionResponse))
        );
    }

    @SuppressWarnings("unchecked")
    private ParsedReply parseReply(Map<String, Object> response) {
        ParsedReply parsed = new ParsedReply();
        Object candidatesObj = response.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) return parsed;

        Object first = candidates.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) return parsed;

        Object contentObj = firstMap.get("content");
        if (!(contentObj instanceof Map<?, ?> contentMap)) return parsed;

        Object partsObj = contentMap.get("parts");
        if (!(partsObj instanceof List<?> parts)) return parsed;

        StringBuilder textBuf = new StringBuilder();
        for (Object p : parts) {
            if (!(p instanceof Map<?, ?> pm)) continue;
            Object fc = pm.get("functionCall");
            if (fc instanceof Map<?, ?> fcMap) {
                parsed.functionCallName = String.valueOf(fcMap.get("name"));
                Object args = fcMap.get("args");
                parsed.functionCallArgs = args instanceof Map ? (Map<String, Object>) args : Map.of();
                return parsed;
            }
            Object text = pm.get("text");
            if (text != null) textBuf.append(text);
        }
        if (textBuf.length() > 0) parsed.text = textBuf.toString();
        return parsed;
    }

    private void updateHistory(String sessionId, String userMessage, String aiMessage) {
        List<Map<String, Object>> history = historyBySession.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(Map.of("role", "user", "parts", List.of(Map.of("text", userMessage))));
        history.add(Map.of("role", "model", "parts", List.of(Map.of("text", aiMessage))));
        while (history.size() > MAX_HISTORY_TURNS * 2) {
            history.remove(0);
        }
    }

    private static class ParsedReply {
        String text;
        String functionCallName;
        Map<String, Object> functionCallArgs;
    }
}
