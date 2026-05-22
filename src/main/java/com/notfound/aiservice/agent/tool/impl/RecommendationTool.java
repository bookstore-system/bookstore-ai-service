package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.BookServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CS-08: Gợi ý sách cá nhân hóa.
 * Kết hợp /books/suggested + /books/best-selling. Tool chỉ trả về dữ liệu,
 * lý do gợi ý sẽ do Gemini sinh ra dựa trên dữ liệu này.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationTool implements Tool {

    public static final String NAME = "recommendationTool";

    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Gợi ý sách cá nhân hóa cho user dựa trên lịch sử + sách bán chạy. Dùng khi user nói 'gợi ý sách cho tôi'.")
                .parameter("limit", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("Số sách trả về cho mỗi nguồn (mặc định 5).")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            int limit = ArgUtil.getInt(arguments, "limit", 5);
            Map<String, Object> suggested = safeCall(() -> bookServiceClient.getSuggested(limit));
            Map<String, Object> bestSelling = safeCall(() -> bookServiceClient.getBestSelling(limit));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", context == null ? null : context.getUserId());
            data.put("suggestedBooks", suggested);
            data.put("bestSellingBooks", bestSelling);
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("recommendationTool failed", e);
            return ToolResult.fail(NAME, "Không lấy được gợi ý: " + e.getMessage());
        }
    }

    private Map<String, Object> safeCall(SupplierWithException<Map<String, Object>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("downstream call failed: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
