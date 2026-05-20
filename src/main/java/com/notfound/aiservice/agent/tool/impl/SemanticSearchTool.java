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
 * CS-07: Tìm kiếm ngữ nghĩa.
 *
 * Vì hệ thống chưa có PGVector/Milvus thực sự, tool này hoạt động ở chế độ
 * "Hybrid": dùng Gemini sinh ra các keyword cốt lõi → gọi search keyword
 * Book-Service. Khi vector DB sẵn sàng, chỉ cần thay implementation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticSearchTool implements Tool {

    public static final String NAME = "semanticSearchTool";

    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Tìm kiếm sách bằng ngữ nghĩa/cảm xúc/bối cảnh (vd: 'cuốn nào giống Harry Potter nhưng bối cảnh Việt Nam'). Dùng khi user không gõ keyword chính xác.")
                .parameter("query", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("Câu mô tả mong muốn của user (ngôn ngữ tự nhiên).")
                        .build())
                .parameter("expandedKeywords", ToolSchema.ParameterSchema.builder()
                        .type("array")
                        .description("Các từ khóa/concept Gemini bóc tách từ query để hỗ trợ tìm kiếm.")
                        .items(ToolSchema.ParameterSchema.builder().type("string").build())
                        .build())
                .parameter("topK", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("Số kết quả tối đa (mặc định 5).")
                        .build())
                .requiredParameter("query")
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            String query = ArgUtil.getString(arguments, "query", "");
            int topK = ArgUtil.getInt(arguments, "topK", 5);

            java.util.List<String> keywords = ArgUtil.getStringList(arguments, "expandedKeywords");
            java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();

            if (keywords.isEmpty()) {
                Map<String, Object> r = bookServiceClient.searchBooks(query, 0, topK);
                results.add(Map.of("keyword", query, "data", r));
            } else {
                for (String kw : keywords) {
                    if (kw == null || kw.isBlank()) continue;
                    try {
                        Map<String, Object> r = bookServiceClient.searchBooks(kw, 0, Math.max(1, topK / keywords.size() + 1));
                        results.add(Map.of("keyword", kw, "data", r));
                    } catch (Exception e) {
                        results.add(Map.of("keyword", kw, "error", e.getMessage()));
                    }
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("query", query);
            data.put("mode", "hybrid-keyword");
            data.put("results", results);
            data.put("note", "Hệ thống đang dùng tìm kiếm hybrid. Khi vector DB sẵn sàng, sẽ chuyển sang true semantic.");
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("semanticSearchTool failed", e);
            return ToolResult.fail(NAME, "Không thực hiện được semantic search: " + e.getMessage());
        }
    }
}
