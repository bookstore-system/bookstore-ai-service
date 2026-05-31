package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.BookServiceClient;
import com.notfound.aiservice.client.ReviewServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CS-11: Tóm tắt đánh giá sách.
 * Tool chỉ chịu trách nhiệm lấy review thô (qua review-service nếu có,
 * hoặc fallback trả averageRating/reviewCount của book). Việc phân tích
 * sentiment do Gemini đảm nhiệm khi nhận data trả về.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSummaryTool implements Tool {

    public static final String NAME = "reviewSummaryTool";

    private final ReviewServiceClient reviewServiceClient;
    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Lấy danh sách review của một cuốn sách để AI tóm tắt sentiment, điểm mạnh/yếu.")
                .parameter("bookId", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("UUID của sách.")
                        .build())
                .parameter("title", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("Tên sách (dùng khi chưa biết ID).")
                        .build())
                .parameter("size", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("Số review tối đa cần lấy (mặc định 30).")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            String bookId = ArgUtil.getString(arguments, "bookId", "");
            String title = ArgUtil.getString(arguments, "title", "");
            int size = ArgUtil.getInt(arguments, "size", 30);

            if (bookId.isBlank() && !title.isBlank()) {
                try {
                    Map<String, Object> r = bookServiceClient.searchBooks(title, 0, 1);
                    bookId = extractFirstBookId(r);
                } catch (Exception e) {
                    log.debug("could not resolve title to bookId: {}", e.getMessage());
                }
            }

            if (bookId.isBlank()) {
                return ToolResult.fail(NAME, "Cần cung cấp bookId hoặc title đủ rõ.");
            }

            final String resolvedBookId = bookId.trim();
            final int resolvedSize = size;
            Map<String, Object> bookDetail = safeCall(() -> bookServiceClient.getBookById(resolvedBookId));
            Map<String, Object> reviews = safeCall(() -> reviewServiceClient.getReviewsByBook(resolvedBookId, 0, resolvedSize));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bookId", bookId);
            data.put("bookDetail", bookDetail);
            data.put("reviews", reviews);
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("reviewSummaryTool failed", e);
            return ToolResult.fail(NAME, "Không lấy được review: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractFirstBookId(Map<String, Object> response) {
        if (response == null) return "";
        Object result = response.get("result");
        if (!(result instanceof Map<?, ?> map)) return "";
        Object content = map.get("content");
        if (!(content instanceof java.util.List<?> list) || list.isEmpty()) return "";
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> book)) return "";
        Object id = ((Map<String, Object>) book).get("id");
        return id == null ? "" : String.valueOf(id);
    }

    private Map<String, Object> safeCall(SupplierWithException<Map<String, Object>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
