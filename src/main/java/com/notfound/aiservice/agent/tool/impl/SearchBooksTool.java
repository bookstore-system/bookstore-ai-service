package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.BookServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * CS-10 (một phần) + tìm kiếm cơ bản.
 * Tìm kiếm sách theo từ khóa qua Book-Service /search.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchBooksTool implements Tool {

    public static final String NAME = "searchBooksTool";

    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Tìm kiếm sách theo từ khóa (tên sách, tác giả, ISBN). Dùng khi user gõ keyword chính xác.")
                .parameter("keyword", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("Từ khóa để tìm kiếm sách.")
                        .build())
                .parameter("page", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("Trang (mặc định 0).")
                        .build())
                .parameter("size", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("Kích thước trang (mặc định 5).")
                        .build())
                .requiredParameter("keyword")
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            String keyword = ArgUtil.getString(arguments, "keyword", "");
            int page = ArgUtil.getInt(arguments, "page", 0);
            int size = ArgUtil.getInt(arguments, "size", 5);
            Map<String, Object> raw = bookServiceClient.searchBooks(keyword, page, size);
            return ToolResult.ok(NAME, Map.of(
                    "keyword", keyword,
                    "books", raw
            ));
        } catch (Exception e) {
            log.warn("searchBooksTool failed", e);
            return ToolResult.fail(NAME, "Không tìm kiếm được sách: " + e.getMessage());
        }
    }
}
