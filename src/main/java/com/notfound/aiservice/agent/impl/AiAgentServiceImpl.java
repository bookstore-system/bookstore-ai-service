package com.notfound.aiservice.agent.impl;

import com.notfound.aiservice.agent.AiAgentService;
import com.notfound.aiservice.agent.BookCardExtractor;
import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolRegistry;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.impl.GuardrailTool;
import com.notfound.aiservice.agent.tool.impl.RecommendationTool;
import com.notfound.aiservice.agent.tool.impl.SearchBooksTool;
import com.notfound.aiservice.model.dto.request.AgentChatRequest;
import com.notfound.aiservice.model.dto.request.AttachmentRequest;
import com.notfound.aiservice.model.dto.response.AgentChatResponse;
import com.notfound.aiservice.model.dto.response.BookCard;
import com.notfound.aiservice.service.AiModelClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl implements AiAgentService {

    private final ToolRegistry toolRegistry;
    private final AiModelClient aiModelClient;
    private final GuardrailTool guardrailTool;
    private final BookCardExtractor bookCardExtractor;

    private final Map<String, List<HistoryMessage>> historyBySession = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_TURNS = 10;

    private static final String SYSTEM_PROMPT = """
            Ban la BookBot - tro ly ao cua nha sach online.
            Nhiem vu:
              1. Hieu intent cua user: tim sach, goi y, so sanh, review, don hang, khuyen mai.
              2. Tu chon tool phu hop khi can du lieu thuc te.
              3. Khong bia thong tin; khi can du lieu sach/don hang/review/khuyen mai thi dung tool.
              4. Neu cau hoi ngoai pham vi nha sach, tu choi lich su va goi y quay lai chu de sach.

            Quy tac tra loi:
              - Tra loi bang tieng Viet than thien, ngắn gọn, có dấu, không sai chính tả.
              - Khi co books trong response, giao dien se hien thi card sach rieng.
              - Vi vay khong liet ke dai dong danh sach sach trong phan text; chi tom tat 1-2 cau.
              - Khong lap lai id/link/gia/rating neu card sach da co thong tin do.
            """;

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        String sessionId = (request.getSessionId() == null || request.getSessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getSessionId();
        String effectiveMessage = resolveFollowUpMessage(sessionId, request.getMessage());
        if (!String.valueOf(request.getMessage()).equals(effectiveMessage)) {
            log.info("Resolved short follow-up message: sessionId={}, original='{}', effective='{}'",
                    sessionId, request.getMessage(), effectiveMessage);
        }

        ToolContext context = ToolContext.builder()
                .sessionId(sessionId)
                .userId(request.getUserId())
                .userMessage(effectiveMessage)
                .attachments(request.getAttachments())
                .build();

        Map<String, Object> guard = guardrailTool.classify(effectiveMessage);
        if ("OUT_OF_SCOPE".equals(guard.get("decision"))) {
            String suggested = (String) guard.get("suggestedResponse");
            return AgentChatResponse.builder()
                    .sessionId(sessionId)
                    .intent("OUT_OF_SCOPE")
                    .response(suggested)
                    .toolCalls(List.of(
                            AgentChatResponse.ToolCallTrace.builder()
                                    .toolName(GuardrailTool.NAME)
                                    .arguments(Map.of("message", effectiveMessage))
                                    .success(true)
                                    .data(guard)
                                    .build()
                    ))
                    .books(List.of())
                    .build();
        }

        if (!aiModelClient.isConfigured()) {
            return AgentChatResponse.builder()
                    .sessionId(sessionId)
                    .intent("CONFIG_ERROR")
                    .response("AI model API key chua duoc cau hinh. Vui long kiem tra lai API KEY cua nha cung cap AI.")
                    .toolCalls(List.of())
                    .books(List.of())
                    .build();
        }

        List<AgentChatResponse.ToolCallTrace> trace = new ArrayList<>();
        String finalAnswer = aiModelClient.chatWithTools(
                SYSTEM_PROMPT,
                formatHistory(sessionId),
                buildUserMessage(request, effectiveMessage),
                toolRegistry.getAll(),
                context,
                trace
        );

        updateHistory(sessionId, request.getMessage(), finalAnswer);

        List<BookCard> books = bookCardExtractor.extract(trace);
        boolean recommendationRequest = looksLikeRecommendationRequest(effectiveMessage);
        String relatedSearchKeyword = extractRelatedSearchKeyword(effectiveMessage);
        int requestedLimit = requestedBookLimit(effectiveMessage);

        log.info(
                "Chatbot agent result before fallback: sessionId={}, recommendationRequest={}, requestedLimit={}, toolCalls={}, extractedBooks={}",
                sessionId,
                recommendationRequest,
                requestedLimit,
                trace.stream().map(AgentChatResponse.ToolCallTrace::getToolName).toList(),
                books.size()
        );

        if (books.isEmpty() && relatedSearchKeyword != null) {
            runSearchFallback(relatedSearchKeyword, context, trace);
            books = bookCardExtractor.extract(trace);
            if (!books.isEmpty()) {
                finalAnswer = buildRelatedSearchAnswer(books, relatedSearchKeyword);
            }
        }

        if (books.isEmpty() && recommendationRequest) {
            runRecommendationFallback(effectiveMessage, context, trace);
            books = bookCardExtractor.extract(trace);
            if (!books.isEmpty()) {
                finalAnswer = buildRecommendationAnswer(books, requestedLimit);
            }
        }
        if (recommendationRequest && books.size() > requestedLimit) {
            books = books.stream().limit(requestedLimit).toList();
        }

        String intent = trace.isEmpty() ? "DIRECT_ANSWER" : trace.get(0).getToolName();
        log.info(
                "Chatbot agent response: sessionId={}, intent={}, finalBooks={}, bookIds={}, bookTitles={}",
                sessionId,
                intent,
                books.size(),
                books.stream().map(BookCard::getId).toList(),
                books.stream().map(BookCard::getTitle).toList()
        );

        return AgentChatResponse.builder()
                .sessionId(sessionId)
                .intent(intent)
                .response(finalAnswer)
                .toolCalls(trace)
                .books(books)
                .build();
    }

    private String buildUserMessage(AgentChatRequest request, String effectiveMessage) {
        StringBuilder message = new StringBuilder(effectiveMessage == null ? "" : effectiveMessage);
        if (request.getAttachments() == null || request.getAttachments().isEmpty()) {
            return message.toString();
        }

        for (AttachmentRequest a : request.getAttachments()) {
            if (a == null) {
                continue;
            }
            if (a.getType() != null && a.getType().toLowerCase().startsWith("image")) {
                message.append("\n[user co gui anh")
                        .append(a.getName() != null ? ": " + a.getName() : "")
                        .append(". Neu can doc anh, hay dung imageScannerTool.]");
            } else {
                message.append("\n[attachment type=")
                        .append(a.getType())
                        .append(a.getName() != null ? ", name=" + a.getName() : "")
                        .append(a.getUrl() != null ? ", url=" + a.getUrl() : "")
                        .append("]");
            }
        }
        return message.toString();
    }

    private void runRecommendationFallback(
            String message,
            ToolContext context,
            List<AgentChatResponse.ToolCallTrace> trace
    ) {
        Tool tool = toolRegistry.get(RecommendationTool.NAME).orElse(null);
        if (tool == null) {
            log.warn("Recommendation fallback skipped: {} is not registered", RecommendationTool.NAME);
            return;
        }

        Map<String, Object> args = Map.of("limit", requestedBookLimit(message));
        ToolResult result;
        try {
            result = tool.execute(args, context);
            log.info(
                    "Recommendation fallback executed: success={}, dataKeys={}",
                    result.isSuccess(),
                    result.getData() == null ? List.of() : result.getData().keySet()
            );
        } catch (Exception e) {
            log.warn("Recommendation fallback failed", e);
            result = ToolResult.fail(RecommendationTool.NAME, e.getMessage());
        }
        trace.add(AgentChatResponse.ToolCallTrace.builder()
                .toolName(RecommendationTool.NAME)
                .arguments(args)
                .success(result.isSuccess())
                .errorMessage(result.getErrorMessage())
                .data(result.getData())
                .build());
    }

    private void runSearchFallback(
            String keyword,
            ToolContext context,
            List<AgentChatResponse.ToolCallTrace> trace
    ) {
        Tool tool = toolRegistry.get(SearchBooksTool.NAME).orElse(null);
        if (tool == null) {
            log.warn("Search fallback skipped: {} is not registered", SearchBooksTool.NAME);
            return;
        }

        Map<String, Object> args = Map.of("keyword", keyword, "page", 0, "size", 5);
        ToolResult result;
        try {
            result = tool.execute(args, context);
            log.info(
                    "Search fallback executed: keyword={}, success={}, dataKeys={}",
                    keyword,
                    result.isSuccess(),
                    result.getData() == null ? List.of() : result.getData().keySet()
            );
        } catch (Exception e) {
            log.warn("Search fallback failed", e);
            result = ToolResult.fail(SearchBooksTool.NAME, e.getMessage());
        }
        trace.add(AgentChatResponse.ToolCallTrace.builder()
                .toolName(SearchBooksTool.NAME)
                .arguments(args)
                .success(result.isSuccess())
                .errorMessage(result.getErrorMessage())
                .data(result.getData())
                .build());
    }

    private String resolveFollowUpMessage(String sessionId, String message) {
        String normalized = normalizeVietnamese(message == null ? "" : message.toLowerCase(Locale.ROOT)).trim();
        if (!isAffirmative(normalized)) {
            return message;
        }

        List<HistoryMessage> history = historyBySession.getOrDefault(sessionId, List.of());
        if (history.isEmpty()) {
            return message;
        }

        String lastAssistant = "";
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryMessage h = history.get(i);
            if ("assistant".equals(h.role())) {
                lastAssistant = normalizeVietnamese(h.text() == null ? "" : h.text().toLowerCase(Locale.ROOT));
                break;
            }
        }

        String previousTopic = extractPreviousUserTopic(history);
        if (previousTopic != null && (lastAssistant.contains("khong co thong tin")
                || lastAssistant.contains("khong tim thay")
                || lastAssistant.contains("muon tim sach khac"))) {
            return "Tim sach lien quan den " + previousTopic + " trong nha sach.";
        }

        if (lastAssistant.contains("muon tim sach khac")
                || lastAssistant.contains("goi y")
                || lastAssistant.contains("gioi thieu")) {
            return "Goi y 2 cuon sach dang co tai nha sach cho toi.";
        }
        return message;
    }

    private boolean isAffirmative(String normalizedMessage) {
        return normalizedMessage.equals("co")
                || normalizedMessage.equals("ok")
                || normalizedMessage.equals("okay")
                || normalizedMessage.equals("duoc")
                || normalizedMessage.equals("dong y")
                || normalizedMessage.equals("vang")
                || normalizedMessage.equals("yes");
    }

    private String extractPreviousUserTopic(List<HistoryMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryMessage h = history.get(i);
            if (!"user".equals(h.role())) {
                continue;
            }
            String topic = extractTopic(h.text());
            if (topic != null) {
                return topic;
            }
        }
        return null;
    }

    private String extractTopic(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String raw = message.trim()
                .replaceAll("(?i)^ban\\s+biet\\s+", "")
                .replaceAll("(?i)^bạn\\s+biết\\s+", "")
                .replaceAll("(?i)^toi\\s+muon\\s+tim\\s+", "")
                .replaceAll("(?i)^tôi\\s+muốn\\s+tìm\\s+", "")
                .replaceAll("(?i)^tim\\s+", "")
                .replaceAll("(?i)^tìm\\s+", "")
                .replaceAll("(?i)\\bkhong\\??$", "")
                .replaceAll("(?i)\\bkhông\\??$", "")
                .replaceAll("[?!.]+$", "")
                .trim();

        raw = raw.replaceAll("(?i)^(truyen|truyện|sach|sách)\\s+", "").trim();
        if (raw.isBlank() || raw.length() > 80) {
            return null;
        }
        return raw;
    }

    private boolean looksLikeRecommendationRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = normalizeVietnamese(message.toLowerCase(Locale.ROOT));
        boolean asksForRecommendation = lower.contains("goi y")
                || lower.contains("gioi thieu")
                || lower.contains("de xuat")
                || lower.contains("sach hay");
        boolean mentionsBooks = lower.contains("sach") || lower.contains("cuon");
        return asksForRecommendation && mentionsBooks;
    }

    private String extractRelatedSearchKeyword(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String lower = normalizeVietnamese(message.toLowerCase(Locale.ROOT));
        String marker = "tim sach lien quan den ";
        int start = lower.indexOf(marker);
        if (start < 0) {
            return null;
        }
        String keyword = message.substring(Math.min(message.length(), start + marker.length())).trim();
        keyword = keyword.replaceAll("(?i)\\s+trong\\s+nha\\s+sach\\.?$", "").trim();
        keyword = keyword.replaceAll("(?i)\\s+trong\\s+nhà\\s+sách\\.?$", "").trim();
        keyword = keyword.replaceAll("[?!.]+$", "").trim();
        return keyword.isBlank() ? null : keyword;
    }

    private int requestedBookLimit(String message) {
        if (message == null) {
            return 2;
        }
        String lower = normalizeVietnamese(message.toLowerCase(Locale.ROOT));
        if (lower.matches(".*\\b(1|mot)\\b.*")) return 1;
        if (lower.matches(".*\\b(3|ba)\\b.*")) return 3;
        if (lower.matches(".*\\b(4|bon)\\b.*")) return 4;
        if (lower.matches(".*\\b(5|nam)\\b.*")) return 5;
        return 2;
    }

    private String normalizeVietnamese(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replace('đ', 'd').replace('Đ', 'D');
    }

    private String buildRecommendationAnswer(List<BookCard> books, int requestedLimit) {
        List<BookCard> picked = books.stream().limit(Math.max(1, requestedLimit)).toList();
        if (picked.isEmpty()) {
            return "Minh chua tim thay sach phu hop de gioi thieu luc nay.";
        }
        String titles = picked.stream()
                .map(BookCard::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .toList()
                .toString();
        return "Mình gợi ý " + picked.size() + " cuốc sách này có tại nhà sách: "
                + titles.substring(1, titles.length() - 1)
                + ". Bạn có thể bấm vào card bên dưới để xem chi tiết.";
    }

    private String buildRelatedSearchAnswer(List<BookCard> books, String keyword) {
        List<BookCard> picked = books.stream().limit(5).toList();
        if (picked.isEmpty()) {
            return "Minh chua tim thay sach lien quan den " + keyword + " trong nha sach.";
        }
        return "Minh tim thay " + picked.size()
                + " sach lien quan den " + keyword
                + ". Ban bam vao card ben duoi de xem chi tiet nhe.";
    }

    private List<String> formatHistory(String sessionId) {
        return historyBySession.getOrDefault(sessionId, List.of()).stream()
                .map(h -> h.role() + ": " + h.text())
                .toList();
    }

    private void updateHistory(String sessionId, String userMessage, String aiMessage) {
        List<HistoryMessage> history = historyBySession.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(new HistoryMessage("user", userMessage));
        history.add(new HistoryMessage("assistant", aiMessage));
        while (history.size() > MAX_HISTORY_TURNS * 2) {
            history.remove(0);
        }
    }

    private record HistoryMessage(String role, String text) {
    }
}
