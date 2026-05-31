package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.BookServiceClient;
import com.notfound.aiservice.client.CartServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddToCartTool implements Tool {

    public static final String NAME = "addToCartTool";

    private final BookServiceClient bookServiceClient;
    private final CartServiceClient cartServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Them mot sach vao gio hang cua user dang dang nhap. User chi can noi ten sach; quantity mac dinh la 1.")
                .parameter("productName", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("Ten sach/san pham user muon them vao gio hang.")
                        .build())
                .parameter("quantity", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("So luong can them, mac dinh 1.")
                        .build())
                .requiredParameter("productName")
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            String userId = context == null ? "" : context.getUserId();
            if (userId == null || userId.isBlank()) {
                return ToolResult.fail(NAME, "Ban can dang nhap de them sach vao gio hang.");
            }

            String productName = ArgUtil.getString(arguments, "productName", "").trim();
            int quantity = Math.max(1, ArgUtil.getInt(arguments, "quantity", 1));
            if (productName.isBlank()) {
                return ToolResult.fail(NAME, "Can ten sach de them vao gio hang.");
            }

            Map<String, Object> searchResponse = bookServiceClient.searchBooks(productName, 0, 3);
            Map<String, Object> matchedBook = firstBook(searchResponse);
            if (matchedBook == null) {
                return ToolResult.fail(NAME, "Khong tim thay sach phu hop voi: " + productName);
            }

            String bookId = firstNonBlank(matchedBook, "id", "bookId", "bookID");
            String title = firstNonBlank(matchedBook, "title", "bookTitle", "name", "bookName");
            if (bookId == null || bookId.isBlank()) {
                return ToolResult.fail(NAME, "Tim thay sach nhung khong doc duoc bookId.");
            }

            Map<String, Object> cartResponse = cartServiceClient.addToCart(
                    userId.trim(),
                    Map.of("bookId", bookId, "quantity", quantity)
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestedProductName", productName);
            data.put("quantity", quantity);
            data.put("matchedBook", matchedBook);
            data.put("bookId", bookId);
            data.put("title", title);
            data.put("cart", cartResponse);
            log.info(
                    "addToCartTool added bookId={}, title='{}', quantity={}, userId={}, cartResponseKeys={}",
                    bookId,
                    title,
                    quantity,
                    userId,
                    cartResponse == null ? List.of() : cartResponse.keySet()
            );
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("addToCartTool failed", e);
            return ToolResult.fail(NAME, "Khong them duoc sach vao gio hang: " + e.getMessage());
        }
    }

    private Map<String, Object> firstBook(Object payload) {
        return firstBook(payload, 0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstBook(Object payload, int depth) {
        if (payload == null || depth > 8) {
            return null;
        }
        if (payload instanceof Collection<?> collection) {
            for (Object item : collection) {
                Map<String, Object> found = firstBook(item, depth + 1);
                if (found != null) return found;
            }
            return null;
        }
        if (payload instanceof Map<?, ?> map) {
            if (looksLikeBook(map)) {
                return (Map<String, Object>) map;
            }
            for (String key : List.of("result", "data", "content", "books", "items", "records")) {
                if (map.containsKey(key)) {
                    Map<String, Object> found = firstBook(map.get(key), depth + 1);
                    if (found != null) return found;
                }
            }
            for (Object value : map.values()) {
                if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
                    Map<String, Object> found = firstBook(value, depth + 1);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private boolean looksLikeBook(Map<?, ?> map) {
        Object id = firstNonBlankValue(map, "id", "bookId", "bookID");
        Object title = firstNonBlankValue(map, "title", "bookTitle", "name", "bookName");
        return id != null && title != null;
    }

    private String firstNonBlank(Map<?, ?> map, String... keys) {
        Object value = firstNonBlankValue(map, keys);
        return value == null ? null : String.valueOf(value);
    }

    private Object firstNonBlankValue(Map<?, ?> map, String... keys) {
        if (map == null || keys == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }
}
