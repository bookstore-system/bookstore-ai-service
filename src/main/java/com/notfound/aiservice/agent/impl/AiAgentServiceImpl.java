package com.notfound.aiservice.agent.impl;

import com.notfound.aiservice.agent.AiAgentService;
import com.notfound.aiservice.agent.BookCardExtractor;
import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolRegistry;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.impl.AddToCartTool;
import com.notfound.aiservice.agent.tool.impl.CategoryBooksTool;
import com.notfound.aiservice.agent.tool.impl.CategoryTool;
import com.notfound.aiservice.agent.tool.impl.GuardrailTool;
import com.notfound.aiservice.agent.tool.impl.ImageScannerTool;
import com.notfound.aiservice.agent.tool.impl.PromotionTool;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
              - Khi user hoi voucher/ma giam gia/khuyen mai hien co, hay goi promotionTool ngay ca khi user chua noi gia tri don hang.
              - Khong hoi lai tong gia tri don hang truoc; neu co voucher active thi gioi thieu voucher hien co truoc.
              - Khi user hoi don hang, chi tra cuu don cua user dang dang nhap bang orderLookupTool; khong hoi/khong dung orderId user nhap.
              - Khi user muon them sach vao gio hang, phai goi addToCartTool voi ten sach user noi; quantity mac dinh la 1 neu user khong noi.
              - Khi user gui anh bia sach, phai goi imageScannerTool de nhan dien va tim sach trong nha sach.
              - Khi user hoi nha sach co nhung the loai/danh muc/category nao, phai goi categoryTool; khong tu bia danh sach the loai.
              - Khi user muon tim/goi y sach theo mot the loai cu the, phai goi categoryBooksTool de lay sach; khong chi liet ke danh muc roi hoi lai.
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
                .authorizationHeader(request.getAuthorizationHeader())
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

        List<BookCard> books = bookCardExtractor.extract(trace);
        boolean recommendationRequest = looksLikeRecommendationRequest(effectiveMessage);
        boolean promotionRequest = looksLikePromotionRequest(effectiveMessage);
        boolean categoryRequest = looksLikeCategoryRequest(effectiveMessage);
        boolean categoryBookSearchRequest = looksLikeCategoryBookSearchRequest(effectiveMessage);
        boolean imageRequest = hasImageAttachment(request);
        boolean addToCartRequest = looksLikeAddToCartRequest(effectiveMessage);
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

        if (imageRequest && trace.stream().noneMatch(t -> ImageScannerTool.NAME.equals(t.getToolName()))) {
            runImageScannerFallback(effectiveMessage, context, trace);
            books = bookCardExtractor.extract(trace);
            if (!books.isEmpty()) {
                finalAnswer = buildImageScanAnswer(books);
            }
        }
        if (imageRequest && books.isEmpty()
                && trace.stream().anyMatch(t -> ImageScannerTool.NAME.equals(t.getToolName()))) {
            finalAnswer = buildImageScanFailureAnswer(trace);
        }

        if (books.isEmpty() && recommendationRequest) {
            runRecommendationFallback(effectiveMessage, context, trace);
            books = bookCardExtractor.extract(trace);
            if (!books.isEmpty()) {
                finalAnswer = buildRecommendationAnswer(books, requestedLimit);
            }
        }
        if (books.isEmpty() && categoryBookSearchRequest) {
            String categoryName = extractCategoryName(effectiveMessage);
            runCategoryBooksFallback(categoryName, context, trace);
            books = bookCardExtractor.extract(trace);
            if (!books.isEmpty()) {
                finalAnswer = buildCategoryBooksAnswer(books, categoryName, requestedLimit);
            }
        }
        if (addToCartRequest && trace.stream().noneMatch(t -> AddToCartTool.NAME.equals(t.getToolName()))) {
            String productName = extractAddToCartProductName(effectiveMessage);
            int quantity = extractQuantity(effectiveMessage);
            runAddToCartFallback(productName, quantity, context, trace);
        }
        if (addToCartRequest && trace.stream().anyMatch(t -> AddToCartTool.NAME.equals(t.getToolName()))) {
            finalAnswer = buildAddToCartAnswer(trace);
        }
        if (promotionRequest && trace.stream().noneMatch(t -> PromotionTool.NAME.equals(t.getToolName()))) {
            runPromotionFallback(context, trace);
        }
        if (promotionRequest && trace.stream().anyMatch(t -> PromotionTool.NAME.equals(t.getToolName()))) {
            finalAnswer = buildPromotionAnswer(trace);
        }
        if (!categoryBookSearchRequest && categoryRequest && trace.stream().noneMatch(t -> CategoryTool.NAME.equals(t.getToolName()))) {
            runCategoryFallback(context, trace);
        }
        if (!categoryBookSearchRequest && categoryRequest && trace.stream().anyMatch(t -> CategoryTool.NAME.equals(t.getToolName()))) {
            finalAnswer = buildCategoryAnswer(trace);
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

        updateHistory(sessionId, request.getMessage(), finalAnswer);

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

    private void runImageScannerFallback(
            String message,
            ToolContext context,
            List<AgentChatResponse.ToolCallTrace> trace
    ) {
        Tool tool = toolRegistry.get(ImageScannerTool.NAME).orElse(null);
        if (tool == null) {
            log.warn("Image scanner fallback skipped: {} is not registered", ImageScannerTool.NAME);
            return;
        }

        Map<String, Object> args = Map.of(
                "hint",
                message == null || message.isBlank()
                        ? "Hay nhan dien bia sach trong anh va tim sach tuong ung trong nha sach."
                        : message
        );
        ToolResult result;
        try {
            result = tool.execute(args, context);
            log.info(
                    "Image scanner fallback executed: success={}, dataKeys={}",
                    result.isSuccess(),
                    result.getData() == null ? List.of() : result.getData().keySet()
            );
        } catch (Exception e) {
            log.warn("Image scanner fallback failed", e);
            result = ToolResult.fail(ImageScannerTool.NAME, e.getMessage());
        }
        trace.add(AgentChatResponse.ToolCallTrace.builder()
                .toolName(ImageScannerTool.NAME)
                .arguments(args)
                .success(result.isSuccess())
                .errorMessage(result.getErrorMessage())
                .data(result.getData())
                .build());
    }

    private void runPromotionFallback(
            ToolContext context,
            List<AgentChatResponse.ToolCallTrace> trace
    ) {
        Tool tool = toolRegistry.get(PromotionTool.NAME).orElse(null);
        if (tool == null) {
            log.warn("Promotion fallback skipped: {} is not registered", PromotionTool.NAME);
            return;
        }

        Map<String, Object> args = Map.of();
        ToolResult result;
        try {
            result = tool.execute(args, context);
            log.info(
                    "Promotion fallback executed: success={}, dataKeys={}",
                    result.isSuccess(),
                    result.getData() == null ? List.of() : result.getData().keySet()
            );
        } catch (Exception e) {
            log.warn("Promotion fallback failed", e);
            result = ToolResult.fail(PromotionTool.NAME, e.getMessage());
        }
        trace.add(AgentChatResponse.ToolCallTrace.builder()
                .toolName(PromotionTool.NAME)
                .arguments(args)
                .success(result.isSuccess())
                .errorMessage(result.getErrorMessage())
                .data(result.getData())
                .build());
    }

    private void runCategoryFallback(
            ToolContext context,
            List<AgentChatResponse.ToolCallTrace> trace
    ) {
        Tool tool = toolRegistry.get(CategoryTool.NAME).orElse(null);
        if (tool == null) {
            log.warn("Category fallback skipped: {} is not registered", CategoryTool.NAME);
            return;
        }

        Map<String, Object> args = Map.of();
        ToolResult result;
        try {
            result = tool.execute(args, context);
            log.info(
                    "Category fallback executed: success={}, dataKeys={}",
                    result.isSuccess(),
                    result.getData() == null ? List.of() : result.getData().keySet()
            );
        } catch (Exception e) {
            log.warn("Category fallback failed", e);
            result = ToolResult.fail(CategoryTool.NAME, e.getMessage());
        }
        trace.add(AgentChatResponse.ToolCallTrace.builder()
                .toolName(CategoryTool.NAME)
                .arguments(args)
                .success(result.isSuccess())
                .errorMessage(result.getErrorMessage())
                .data(result.getData())
                .build());
    }

    private void runCategoryBooksFallback(
            String categoryName,
            ToolContext context,
            List<AgentChatResponse.ToolCallTrace> trace
    ) {
        Tool tool = toolRegistry.get(CategoryBooksTool.NAME).orElse(null);
        if (tool == null) {
            log.warn("Category books fallback skipped: {} is not registered", CategoryBooksTool.NAME);
            return;
        }

        String resolvedCategoryName = categoryName == null || categoryName.isBlank()
                ? String.valueOf(context.getUserMessage())
                : categoryName;
        Map<String, Object> args = Map.of(
                "categoryName", resolvedCategoryName,
                "size", 5
        );
        ToolResult result;
        try {
            result = tool.execute(args, context);
            log.info(
                    "Category books fallback executed: categoryName={}, success={}, dataKeys={}",
                    categoryName,
                    result.isSuccess(),
                    result.getData() == null ? List.of() : result.getData().keySet()
            );
        } catch (Exception e) {
            log.warn("Category books fallback failed", e);
            result = ToolResult.fail(CategoryBooksTool.NAME, e.getMessage());
        }
        trace.add(AgentChatResponse.ToolCallTrace.builder()
                .toolName(CategoryBooksTool.NAME)
                .arguments(args)
                .success(result.isSuccess())
                .errorMessage(result.getErrorMessage())
                .data(result.getData())
                .build());
    }

    private void runAddToCartFallback(
            String productName,
            int quantity,
            ToolContext context,
            List<AgentChatResponse.ToolCallTrace> trace
    ) {
        Tool tool = toolRegistry.get(AddToCartTool.NAME).orElse(null);
        if (tool == null) {
            log.warn("Add-to-cart fallback skipped: {} is not registered", AddToCartTool.NAME);
            return;
        }

        Map<String, Object> args = Map.of(
                "productName", productName == null || productName.isBlank() ? context.getUserMessage() : productName,
                "quantity", Math.max(1, quantity)
        );
        ToolResult result;
        try {
            result = tool.execute(args, context);
            log.info(
                    "Add-to-cart fallback executed: productName={}, quantity={}, success={}, dataKeys={}",
                    productName,
                    quantity,
                    result.isSuccess(),
                    result.getData() == null ? List.of() : result.getData().keySet()
            );
        } catch (Exception e) {
            log.warn("Add-to-cart fallback failed", e);
            result = ToolResult.fail(AddToCartTool.NAME, e.getMessage());
        }
        trace.add(AgentChatResponse.ToolCallTrace.builder()
                .toolName(AddToCartTool.NAME)
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

    private boolean looksLikePromotionRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = normalizeVietnamese(message.toLowerCase(Locale.ROOT));
        return lower.contains("voucher")
                || lower.contains("coupon")
                || lower.contains("ma giam gia")
                || lower.contains("giam gia")
                || lower.contains("khuyen mai")
                || lower.contains("uu dai");
    }

    private boolean looksLikeAddToCartRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = normalizeVietnamese(message.toLowerCase(Locale.ROOT));
        boolean addIntent = lower.contains("them")
                || lower.contains("bo vao")
                || lower.contains("cho vao")
                || lower.contains("dat vao");
        boolean cartIntent = lower.contains("gio hang")
                || lower.contains("gio")
                || lower.contains("cart");
        return addIntent && cartIntent;
    }

    private String extractAddToCartProductName(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String cleaned = message.trim();
        cleaned = cleaned.replaceAll("(?i)\\b(thêm|them|bỏ|bo|cho|đặt|dat)\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(vào|vao|giỏ hàng|gio hang|giỏ|gio|cart|hàng|hang|sách|sach|cuốn|cuon|quyển|quyen)\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(cho tôi|cho toi|giúp tôi|giup toi|giùm tôi|gium toi|với|voi|nhé|nhe)\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(số lượng|so luong|sl|quantity)\\b\\s*[:=]?\\s*\\d+", " ");
        cleaned = cleaned.replaceAll("\\b\\d+\\b", " ");
        cleaned = cleaned.replaceAll("[\"“”'.,!?;:()\\[\\]{}]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private int extractQuantity(String message) {
        if (message == null || message.isBlank()) {
            return 1;
        }
        String normalized = normalizeVietnamese(message.toLowerCase(Locale.ROOT));
        Matcher explicit = Pattern.compile("(?:so luong|sl|quantity)\\s*[:=]?\\s*(\\d+)").matcher(normalized);
        if (explicit.find()) {
            return Math.max(1, Integer.parseInt(explicit.group(1)));
        }
        Matcher numberWithUnit = Pattern.compile("\\b(\\d+)\\s*(?:cuon|quyen|sach|sp|san pham)?\\b").matcher(normalized);
        if (numberWithUnit.find()) {
            return Math.max(1, Integer.parseInt(numberWithUnit.group(1)));
        }
        if (normalized.matches(".*\\b(hai|doi|2)\\b.*")) return 2;
        if (normalized.matches(".*\\b(ba|3)\\b.*")) return 3;
        if (normalized.matches(".*\\b(bon|4)\\b.*")) return 4;
        if (normalized.matches(".*\\b(nam|5)\\b.*")) return 5;
        return 1;
    }

    private boolean hasImageAttachment(AgentChatRequest request) {
        if (request == null || request.getAttachments() == null || request.getAttachments().isEmpty()) {
            return false;
        }
        return request.getAttachments().stream()
                .anyMatch(a -> a != null
                        && a.getType() != null
                        && a.getType().toLowerCase(Locale.ROOT).startsWith("image"));
    }

    private boolean looksLikeCategoryRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = normalizeVietnamese(message.toLowerCase(Locale.ROOT));
        return lower.contains("the loai")
                || lower.contains("danh muc")
                || lower.contains("category")
                || lower.contains("genre")
                || lower.contains("loai sach")
                || lower.contains("muc sach");
    }

    private boolean looksLikeCategoryBookSearchRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = normalizeVietnamese(message.toLowerCase(Locale.ROOT));
        boolean asksForBooks = lower.contains("tim sach")
                || lower.contains("tim kiem sach")
                || lower.contains("goi y sach")
                || lower.contains("gioi thieu sach")
                || lower.contains("sach co the loai")
                || lower.contains("sach thuoc")
                || lower.contains("cuon sach");
        return asksForBooks && looksLikeCategoryRequest(message);
    }

    private String extractCategoryName(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String normalized = normalizeVietnamese(message.toLowerCase(Locale.ROOT));
        for (String marker : List.of("the loai", "danh muc", "category", "genre", "loai sach")) {
            int index = normalized.lastIndexOf(marker);
            if (index >= 0) {
                String raw = message.substring(Math.min(message.length(), index + marker.length())).trim();
                raw = raw.replaceAll("(?i)^\\s*(la|là|:|-|=|co|có|thuoc|thuộc)\\s+", "").trim();
                raw = raw.replaceAll("[?!.]+$", "").trim();
                if (!raw.isBlank()) {
                    return raw;
                }
            }
        }
        return message;
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
            return "Mình chưa tìm thấy sách phù hợp để giới thiệu lúc này.";
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
            return "Mình chưa tìm thấy sách liên quan đến " + keyword + " trong nhà sách.";
        }
        return "Mình tìm thấy " + picked.size()
                + " sách liên quan đến " + keyword
                + ". Bạn bấm vào card bên dưới để xem chi tiết nhé.";
    }

    private String buildImageScanAnswer(List<BookCard> books) {
        List<BookCard> picked = books.stream().limit(3).toList();
        if (picked.isEmpty()) {
            return "Mình đã phân tích ảnh nhưng chưa tìm thấy sách trùng khớp trong nhà sách.";
        }
        return "Mình đã phân tích ảnh và tìm thấy " + picked.size()
                + " sách có thể phù hợp. Bạn bấm vào card bên dưới để xem chi tiết nhé.";
    }

    private String buildImageScanFailureAnswer(List<AgentChatResponse.ToolCallTrace> trace) {
        AgentChatResponse.ToolCallTrace imageTrace = trace.stream()
                .filter(t -> ImageScannerTool.NAME.equals(t.getToolName()))
                .reduce((first, second) -> second)
                .orElse(null);
        if (imageTrace == null) {
            return "Mình chưa phân tích được ảnh. Bạn thử gửi lại ảnh bìa sách rõ hơn nhe.";
        }
        String error = imageTrace.getErrorMessage();
        if (error != null && error.contains("MULTIMODAL_UNSUPPORTED")) {
            return "Hien tai AI dang chay bang provider khong ho tro doc anh. Vui long chuyen AI_PROVIDER=gemini de minh co the phan tich anh bia sach.";
        }
        if (imageTrace.getData() != null) {
            Object inferredTitle = imageTrace.getData().get("inferredTitle");
            if (inferredTitle != null && !String.valueOf(inferredTitle).isBlank()) {
                return "Mình nhan dien duoc ten sach la \"" + inferredTitle
                        + "\" nhung chua tim thay sach trung khop trong nha sach.";
            }
        }
        return "Mình đã phân tích ảnh nhưng chưa nhận diện được tên sách rõ rang. ạn thử gửi ảnh bìa sách rõ hơn hoặc nhập tên sách để mình tìm tiếp nhe.";
    }

    private String buildAddToCartAnswer(List<AgentChatResponse.ToolCallTrace> trace) {
        AgentChatResponse.ToolCallTrace cartTrace = trace.stream()
                .filter(t -> AddToCartTool.NAME.equals(t.getToolName()))
                .reduce((first, second) -> second)
                .orElse(null);
        if (cartTrace == null) {
            return "Mình chưa thêm được sách vào giỏ hàng.";
        }
        if (!cartTrace.isSuccess()) {
            String error = cartTrace.getErrorMessage();
            if (error != null && error.contains("dang nhap")) {
                return "Bạn cần đăng nhập để thêm sách vào giỏ hàng.";
            }
            return "Mình chưa thêm được sách vào giỏ hàng: "
                    + (error == null || error.isBlank() ? "không tìm thấy sách phù hợp." : error);
        }
        Map<String, Object> data = cartTrace.getData();
        String title = data == null ? null : String.valueOf(data.getOrDefault("title", ""));
        Object quantity = data == null ? 1 : data.getOrDefault("quantity", 1);
        String bookText = title == null || title.isBlank() ? "sach nay" : "\"" + title + "\"";
        return "Mình đã thêm " + quantity + " cuốn " + bookText
                + " vào giỏ hàng của bạn.";
    }

    private String buildCategoryBooksAnswer(List<BookCard> books, String categoryName, int requestedLimit) {
        List<BookCard> picked = books.stream().limit(Math.max(1, requestedLimit)).toList();
        if (picked.isEmpty()) {
            return "Mình chưa tìm thấy sách phù hợp với thể loại này.";
        }
        String categoryText = categoryName == null || categoryName.isBlank()
                ? "thể loại này"
                : "thể loại " + categoryName;
        return "Mình tìm thấy " + picked.size()
                + " sách thuộc " + categoryText
                + ". Bấm vào card bên dưới để xem chi tiết nhé.";
    }

    @SuppressWarnings("unchecked")
    private String buildPromotionAnswer(List<AgentChatResponse.ToolCallTrace> trace) {
        AgentChatResponse.ToolCallTrace promotionTrace = trace.stream()
                .filter(t -> PromotionTool.NAME.equals(t.getToolName()))
                .reduce((first, second) -> second)
                .orElse(null);
        if (promotionTrace == null || promotionTrace.getData() == null) {
            return "Hiện tại nhà sách chưa có voucher/khuyến mãi nào đang áp dụng. Tuy nhiên, bạn có thể kiểm tra lại sau hoặc xem thêm các sách khác mà bạn quan tâm.";
        }

        Object warning = promotionTrace.getData().get("warning");
        if (warning != null) {
            return String.valueOf(warning);
        }

        Object rawPromotions = promotionTrace.getData().get("activePromotions");
        if (!(rawPromotions instanceof List<?> rawList) || rawList.isEmpty()) {
            return "Hiện tại nhà sách chưa có voucher nào đang áp dụng.";
        }

        List<Map<String, Object>> promotions = rawList.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .limit(5)
                .toList();
        String summaries = promotions.stream()
                .map(this::promotionSummary)
                .filter(s -> s != null && !s.isBlank())
                .toList()
                .toString();
        if (summaries.length() <= 2) {
            return "Mình đã tìm thấy " + rawList.size()
                    + " voucher đang áp dụng. Bạn có thể vào giỏ hàng để chọn mã phù hợp khi thanh toán.";
        }
        return "Hiện tại có " + rawList.size() + " voucher đang áp dụng, gồm: "
                + summaries.substring(1, summaries.length() - 1)
                + ". Bạn có thể chọn voucher phù hợp khi thanh toán.";
    }

    @SuppressWarnings("unchecked")
    private String buildCategoryAnswer(List<AgentChatResponse.ToolCallTrace> trace) {
        AgentChatResponse.ToolCallTrace categoryTrace = trace.stream()
                .filter(t -> CategoryTool.NAME.equals(t.getToolName()))
                .reduce((first, second) -> second)
                .orElse(null);
        if (categoryTrace == null || !categoryTrace.isSuccess() || categoryTrace.getData() == null) {
            return "Mình chưa lấy được danh sách thể loại sách hiện có lúc này.";
        }

        Object rawCategories = categoryTrace.getData().get("categories");
        if (!(rawCategories instanceof List<?> rawList) || rawList.isEmpty()) {
            return "Hiện tại nhà sách chưa có danh mục/sách nào được phân loại sẵn. Bạn có thể tìm kiếm trực tiếp tên sách hoặc thể loại bạn quan tâm để mình giúp nhé.";
        }

        List<String> names = rawList.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(category -> firstNonBlank(category, "name", "title"))
                .filter(name -> name != null && !name.isBlank())
                .limit(12)
                .toList();
        if (names.isEmpty()) {
            return "Mình tìm thấy " + rawList.size()
                    + " danh mục sách, nhưng chưa đọc được tên danh mục để hiển thị.";
        }
        String joined = names.toString();
        return "Hiện tại nhà sách đang có " + rawList.size()
                + " thể loại sách, gồm: "
                + joined.substring(1, joined.length() - 1)
                + ". Bạn muốn mình gợi ý sách theo thể loại nào?";
    }

    private String promotionSummary(Map<String, Object> promotion) {
        String title = firstNonBlank(promotion, "name", "title", "promotionName", "description");
        String code = firstNonBlank(promotion, "code", "couponCode", "promotionCode");
        String discount = promotionDiscountText(promotion);
        String endDate = firstNonBlank(promotion, "endDate", "expiredAt", "expiresAt");
        String usage = promotionUsageText(promotion);

        StringBuilder summary = new StringBuilder();
        if (title != null) {
            summary.append("\"").append(title).append("\"");
        } else if (code != null) {
            summary.append("voucher ").append(code);
        } else {
            Object id = promotion.get("promotionID");
            summary.append(id == null ? "một voucher ưu đãi" : "voucher #" + id);
        }

        if (code != null) {
            summary.append(" - mã ").append(code);
        }
        if (discount != null) {
            summary.append(", ").append(discount);
        }
        if (endDate != null) {
            summary.append(", hạn đến ").append(endDate);
        }
        // if (usage != null) {
        //     summary.append(", ").append(usage);
        // }
        return summary.toString();
    }

    private String firstNonBlank(Map<String, Object> promotion, String... keys) {
        for (String key : keys) {
            Object value = promotion.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String promotionDiscountText(Map<String, Object> promotion) {
        Object percent = promotion.get("discountPercent");
        if (percent == null) {
            percent = promotion.get("discountPercentage");
        }
        if (percent != null) {
            return "giảm " + trimNumber(percent) + "%";
        }

        Object value = promotion.get("discountValue");
        if (value == null) {
            value = promotion.get("discountAmount");
        }
        if (value == null) {
            return null;
        }

        String type = firstNonBlank(promotion, "discountType", "type");
        if (type != null && type.toUpperCase(Locale.ROOT).contains("PERCENT")) {
            return "giảm " + trimNumber(value) + "%";
        }
        return "giảm " + trimNumber(value) + " VND";
    }

    private String promotionUsageText(Map<String, Object> promotion) {
        Object usageLimit = promotion.get("usageLimit");
        if (usageLimit == null) {
            return null;
        }
        Object usageCount = promotion.get("usageCount");
        if (usageCount == null) {
            return "giới hạn " + trimNumber(usageLimit) + " lượt";
        }
        return "còn khoảng " + Math.max(0, asInt(usageLimit) - asInt(usageCount)) + " lượt";
    }

    private String trimNumber(Object value) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (d == Math.rint(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return String.valueOf(value);
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
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
