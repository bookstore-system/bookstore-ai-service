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

@Slf4j
@Component
@RequiredArgsConstructor
public class BookDetailTool implements Tool {

    public static final String NAME = "bookDetailTool";

    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Lay thong tin chi tiet cua mot cuon sach theo bookId. Dung khi user hoi mo ta, noi dung, tac gia, gia, ton kho, review count, hoac hoi tiep ve 'cuon nay'.")
                .parameter("bookId", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("UUID cua sach can lay chi tiet.")
                        .build())
                .requiredParameter("bookId")
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            String bookId = ArgUtil.getString(arguments, "bookId", "");
            if (bookId == null || bookId.isBlank()) {
                return ToolResult.fail(NAME, "Thieu bookId de lay chi tiet sach.");
            }
            Map<String, Object> detail = bookServiceClient.getBookById(bookId.trim());
            return ToolResult.ok(NAME, Map.of(
                    "bookId", bookId.trim(),
                    "book", detail
            ));
        } catch (Exception e) {
            log.warn("bookDetailTool failed", e);
            return ToolResult.fail(NAME, "Khong lay duoc chi tiet sach: " + e.getMessage());
        }
    }
}
