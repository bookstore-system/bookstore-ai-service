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
 * CS-10 (phần hậu kỳ) + kiểm tồn kho.
 * Cho phép lọc sách theo điều kiện (giá, rating, category) và đảm bảo còn hàng,
 * hoặc tra cứu nhanh tồn kho theo bookId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockCheckTool implements Tool {

    public static final String NAME = "stockCheckTool";

    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Kiểm tra tồn kho và lọc sách theo giá/category. Dùng khi user nói 'còn hàng không', 'dưới 200k', 'thể loại kinh tế'.")
                .parameter("bookId", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("UUID của sách cần check stock cụ thể.")
                        .build())
                .parameter("keyword", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("Từ khóa kèm theo (vd: 'kinh tế').")
                        .build())
                .parameter("minPrice", ToolSchema.ParameterSchema.builder()
                        .type("number")
                        .description("Giá tối thiểu (VNĐ).")
                        .build())
                .parameter("maxPrice", ToolSchema.ParameterSchema.builder()
                        .type("number")
                        .description("Giá tối đa (VNĐ).")
                        .build())
                .parameter("minRating", ToolSchema.ParameterSchema.builder()
                        .type("number")
                        .description("Số sao tối thiểu (0-5).")
                        .build())
                .parameter("categoryId", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("UUID của thể loại.")
                        .build())
                .parameter("size", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("Số kết quả tối đa (mặc định 10).")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            String bookId = ArgUtil.getString(arguments, "bookId", "");
            if (!bookId.isBlank()) {
                Map<String, Object> detail = bookServiceClient.getBookById(bookId.trim());
                return ToolResult.ok(NAME, Map.of(
                        "mode", "single",
                        "bookId", bookId,
                        "book", detail
                ));
            }

            String keyword = ArgUtil.getString(arguments, "keyword", null);
            Double minPrice = ArgUtil.getDouble(arguments, "minPrice", null);
            Double maxPrice = ArgUtil.getDouble(arguments, "maxPrice", null);
            Double minRating = ArgUtil.getDouble(arguments, "minRating", null);
            String categoryId = ArgUtil.getString(arguments, "categoryId", null);
            int size = ArgUtil.getInt(arguments, "size", 10);

            Map<String, Object> response = bookServiceClient.filterBooks(
                    blankToNull(keyword), minPrice, maxPrice, minRating,
                    blankToNull(categoryId), 0, size
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("mode", "filter");
            data.put("criteria", Map.of(
                    "keyword", keyword == null ? "" : keyword,
                    "minPrice", minPrice,
                    "maxPrice", maxPrice,
                    "minRating", minRating,
                    "categoryId", categoryId == null ? "" : categoryId
            ));
            data.put("books", response);
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("stockCheckTool failed", e);
            return ToolResult.fail(NAME, "Không kiểm tra được tồn kho: " + e.getMessage());
        }
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
