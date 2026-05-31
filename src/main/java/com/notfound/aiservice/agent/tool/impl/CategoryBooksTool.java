package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.BookServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryBooksTool implements Tool {

    public static final String NAME = "categoryBooksTool";

    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Tim sach theo danh muc/the loai that trong nha sach. Dung khi user yeu cau tim/goi y sach thuoc mot the loai cu the.")
                .parameter("categoryName", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("Ten the loai user muon tim, vi du: Truyen tranh, Sach thieu nhi.")
                        .build())
                .parameter("size", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("So sach can lay, mac dinh 5.")
                        .build())
                .requiredParameter("categoryName")
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            String categoryName = ArgUtil.getString(arguments, "categoryName", "");
            int size = ArgUtil.getInt(arguments, "size", 5);

            Map<String, Object> categoryResponse = bookServiceClient.getCategories();
            Object rawCategories = firstNonNull(
                    categoryResponse == null ? null : categoryResponse.get("result"),
                    categoryResponse == null ? null : categoryResponse.get("data")
            );
            List<Map<String, Object>> categories = rawCategories instanceof List<?> list
                    ? (List<Map<String, Object>>) list
                    : List.of();
            Map<String, Object> matched = findBestCategory(categories, categoryName);
            if (matched == null) {
                return ToolResult.fail(NAME, "Khong tim thay the loai phu hop voi: " + categoryName);
            }

            String categoryId = String.valueOf(matched.get("id"));
            Map<String, Object> books = bookServiceClient.filterBooks(
                    null,
                    null,
                    null,
                    null,
                    categoryId,
                    0,
                    size
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestedCategoryName", categoryName);
            data.put("matchedCategory", matched);
            data.put("categoryId", categoryId);
            data.put("books", books);
            log.info(
                    "categoryBooksTool fetched categoryName='{}', matched='{}', categoryId={}, bookResponseKeys={}",
                    categoryName,
                    matched.get("name"),
                    categoryId,
                    books == null ? List.of() : books.keySet()
            );
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("categoryBooksTool failed", e);
            return ToolResult.fail(NAME, "Khong tim duoc sach theo the loai: " + e.getMessage());
        }
    }

    private Map<String, Object> findBestCategory(List<Map<String, Object>> categories, String query) {
        String normalizedQuery = normalize(query);
        Map<String, Object> best = null;
        int bestScore = 0;
        for (Map<String, Object> category : categories) {
            String name = String.valueOf(category.getOrDefault("name", ""));
            String normalizedName = normalize(name);
            int score = matchScore(normalizedQuery, normalizedName);
            if (score > bestScore) {
                best = category;
                bestScore = score;
            }
        }
        return bestScore <= 0 ? null : best;
    }

    private int matchScore(String query, String categoryName) {
        if (query.isBlank() || categoryName.isBlank()) {
            return 0;
        }
        if (query.equals(categoryName)) {
            return 100;
        }
        if (query.contains(categoryName)) {
            return 80 + categoryName.length();
        }
        if (categoryName.contains(query)) {
            return 60 + query.length();
        }
        int score = 0;
        for (String token : categoryName.split("\\s+")) {
            if (token.length() >= 3 && query.contains(token)) {
                score += 10;
            }
        }
        return score;
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
