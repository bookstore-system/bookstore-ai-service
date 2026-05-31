package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.BookServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryTool implements Tool {

    public static final String NAME = "categoryTool";

    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Lay danh sach danh muc/the loai sach that dang co trong nha sach. Dung khi user hoi nha sach co nhung the loai/danh muc nao.")
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            Map<String, Object> response = bookServiceClient.getCategories();
            Object result = response == null ? null : response.get("result");
            if (result == null && response != null) {
                result = response.get("data");
            }
            List<Map<String, Object>> categories = result instanceof List<?> list
                    ? (List<Map<String, Object>>) list
                    : List.of();
            log.info(
                    "categoryTool fetched categories={}, responseKeys={}, names={}",
                    categories.size(),
                    response == null ? List.of() : response.keySet(),
                    categories.stream()
                            .map(category -> category.get("name"))
                            .filter(name -> name != null && !String.valueOf(name).isBlank())
                            .toList()
            );
            return ToolResult.ok(NAME, Map.of(
                    "categories", categories,
                    "totalCategories", categories.size()
            ));
        } catch (Exception e) {
            log.warn("categoryTool failed: {}", e.getMessage());
            return ToolResult.fail(NAME, "Khong lay duoc danh sach the loai: " + e.getMessage());
        }
    }
}
