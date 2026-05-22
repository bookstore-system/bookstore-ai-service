package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.BookServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CS-09: So sánh các cuốn sách.
 * Cho phép truyền vào danh sách tiêu đề (titles) hoặc danh sách bookIds.
 *  - Nếu có bookIds: gọi batch-details
 *  - Nếu chỉ có titles: tìm kiếm từng tên rồi trả về top match
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompareBooksTool implements Tool {

    public static final String NAME = "compareBooksTool";

    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("So sánh nhiều cuốn sách dựa trên tên hoặc ID. Dùng khi user hỏi 'nên mua A hay B'.")
                .parameter("titles", ToolSchema.ParameterSchema.builder()
                        .type("array")
                        .description("Danh sách tên sách cần so sánh.")
                        .items(ToolSchema.ParameterSchema.builder().type("string").build())
                        .build())
                .parameter("bookIds", ToolSchema.ParameterSchema.builder()
                        .type("array")
                        .description("Danh sách ID sách (UUID) nếu đã biết.")
                        .items(ToolSchema.ParameterSchema.builder().type("string").build())
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            List<String> bookIds = ArgUtil.getStringList(arguments, "bookIds");
            List<String> titles = ArgUtil.getStringList(arguments, "titles");

            List<Map<String, Object>> books = new ArrayList<>();

            if (!bookIds.isEmpty()) {
                Map<String, Object> response = bookServiceClient.getBatchBookDetails(Map.of("bookIds", bookIds));
                books.add(Map.of("source", "batch-details", "data", response));
            }

            if (!titles.isEmpty()) {
                for (String title : titles) {
                    try {
                        Map<String, Object> r = bookServiceClient.searchBooks(title, 0, 1);
                        books.add(Map.of("title", title, "data", r));
                    } catch (Exception e) {
                        log.debug("search for title '{}' failed: {}", title, e.getMessage());
                        books.add(Map.of("title", title, "error", e.getMessage()));
                    }
                }
            }

            if (books.isEmpty()) {
                return ToolResult.fail(NAME, "Cần truyền titles hoặc bookIds để so sánh.");
            }

            return ToolResult.ok(NAME, Map.of(
                    "comparison", books,
                    "criteria", List.of("price", "rating", "stock", "category", "audience")
            ));
        } catch (Exception e) {
            log.warn("compareBooksTool failed", e);
            return ToolResult.fail(NAME, "Không so sánh được sách: " + e.getMessage());
        }
    }
}
