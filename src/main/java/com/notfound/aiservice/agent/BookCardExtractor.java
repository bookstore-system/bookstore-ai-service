package com.notfound.aiservice.agent;

import com.notfound.aiservice.model.dto.response.AgentChatResponse;
import com.notfound.aiservice.model.dto.response.BookCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gom dữ liệu sách từ trace tool calls của agent thành {@link BookCard}
 * chuẩn hoá để FE render card mà không phải parse từng shape data riêng
 * cho từng tool.
 *
 * Mỗi tool trả sách theo cấu trúc khác nhau (xem từng tool impl):
 *  - searchBooksTool / stockCheckTool(filter) : data.books -> PageResponse.result.content
 *  - semanticSearchTool                       : data.results[].data -> PageResponse
 *  - recommendationTool                       : data.suggestedBooks / bestSellingBooks
 *  - compareBooksTool                         : data.comparison[].data
 *  - imageScannerTool                         : data.matchedBooks
 *  - stockCheckTool(single)                   : data.book (ApiResponse.result là object đơn)
 *
 * Bộ extractor xử lý toàn bộ các shape trên, dedupe theo id, giới hạn 12 card.
 */
@Slf4j
@Component
public class BookCardExtractor {

    private static final int MAX_CARDS = 12;

    public List<BookCard> extract(List<AgentChatResponse.ToolCallTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            log.info("BookCardExtractor: no tool traces, books=0");
            return List.of();
        }

        LinkedHashMap<String, BookCard> dedup = new LinkedHashMap<>();
        for (AgentChatResponse.ToolCallTrace t : traces) {
            if (t == null || !t.isSuccess() || t.getData() == null) continue;
            try {
                log.info(
                        "BookCardExtractor: reading tool={}, dataKeys={}",
                        t.getToolName(),
                        t.getData().keySet()
                );
                extractFromOne(t.getToolName(), t.getData(), dedup);
            } catch (Exception e) {
                log.debug("BookCardExtractor: failed to read tool {} data: {}",
                        t.getToolName(), e.getMessage());
            }
            if (dedup.size() >= MAX_CARDS) break;
        }
        log.info(
                "BookCardExtractor: extracted books={}, ids={}, titles={}",
                dedup.size(),
                dedup.values().stream().map(BookCard::getId).toList(),
                dedup.values().stream().map(BookCard::getTitle).toList()
        );
        return new ArrayList<>(dedup.values());
    }

    private void extractFromOne(String toolName, Map<String, Object> data,
                                LinkedHashMap<String, BookCard> dedup) {
        if (toolName == null) return;
        switch (toolName) {
            case "searchBooksTool" -> drillPage(toolName, data.get("books"), dedup);
            case "semanticSearchTool" -> {
                Object results = data.get("results");
                if (results instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            drillPage(toolName, m.get("data"), dedup);
                        }
                    }
                }
            }
            case "stockCheckTool" -> {
                Object mode = data.get("mode");
                if ("filter".equals(mode)) {
                    drillPage(toolName, data.get("books"), dedup);
                } else if ("single".equals(mode)) {
                    Object book = data.get("book");
                    if (book instanceof Map<?, ?> bookMap) {
                        Object inner = bookMap.get("result");
                        pushBook(toolName, inner instanceof Map ? (Map<?, ?>) inner : bookMap, dedup);
                    }
                }
            }
            case "recommendationTool" -> {
                drillPage(toolName, data.get("suggestedBooks"), dedup);
                drillPage(toolName, data.get("bestSellingBooks"), dedup);
            }
            case "categoryBooksTool" -> drillPage(toolName, data.get("books"), dedup);
            case "compareBooksTool" -> {
                Object comparison = data.get("comparison");
                if (comparison instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            drillPage(toolName, m.get("data"), dedup);
                        }
                    }
                }
            }
            case "imageScannerTool" -> drillPage(toolName, data.get("matchedBooks"), dedup);
            default -> {}
        }
    }

    /**
     * `payload` có thể là:
     *  - ApiResponse: { code, message, result: PageResponse }
     *  - PageResponse: { content: [...], currentPage, ... }
     *  - List<Book>: [ ... ]
     *  - Book object: { id, title, ... }
     */
    private void drillPage(String toolName, Object payload,
                           LinkedHashMap<String, BookCard> dedup) {
        drillAny(toolName, payload, dedup, 0);
    }

    private void drillAny(String toolName, Object payload,
                          LinkedHashMap<String, BookCard> dedup,
                          int depth) {
        if (payload == null || dedup.size() >= MAX_CARDS || depth > 8) return;

        if (payload instanceof Collection<?> coll) {
            for (Object item : coll) {
                drillAny(toolName, item, dedup, depth + 1);
                if (dedup.size() >= MAX_CARDS) return;
            }
            return;
        }

        if (payload instanceof Map<?, ?> map) {
            if (looksLikeBook(map)) {
                pushBook(toolName, map, dedup);
                return;
            }

            for (String key : List.of(
                    "result",
                    "data",
                    "content",
                    "books",
                    "items",
                    "records",
                    "suggestedBooks",
                    "bestSellingBooks"
            )) {
                if (map.containsKey(key)) {
                    drillAny(toolName, map.get(key), dedup, depth + 1);
                    if (dedup.size() >= MAX_CARDS) return;
                }
            }

            for (Object value : map.values()) {
                if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
                    drillAny(toolName, value, dedup, depth + 1);
                    if (dedup.size() >= MAX_CARDS) return;
                }
            }
        }
    }

    private boolean looksLikeBook(Map<?, ?> map) {
        return map.containsKey("id") && firstNonBlank(
                map.get("title"),
                map.get("bookTitle"),
                map.get("name"),
                map.get("bookName")
        ) != null;
    }

    private void pushBook(String toolName, Object raw,
                          LinkedHashMap<String, BookCard> dedup) {
        if (!(raw instanceof Map<?, ?> book)) return;
        if (dedup.size() >= MAX_CARDS) return;

        String id = asString(book.get("id"));
        String title = firstNonBlank(
                book.get("title"),
                book.get("bookTitle"),
                book.get("name"),
                book.get("bookName")
        );
        if (id == null || id.isBlank() || title == null || title.isBlank()) return;
        if (dedup.containsKey(id)) return;

        BookCard card = BookCard.builder()
                .id(id)
                .title(title)
                .price(asDouble(book.get("price")))
                .discountPrice(asDouble(book.get("discountPrice")))
                .mainImageUrl(resolveMainImage(book))
                .averageRating(asDouble(book.get("averageRating")))
                .reviewCount(asInteger(book.get("reviewCount")))
                .stockQuantity(asInteger(book.get("stockQuantity")))
                .authorNames(asStringList(book.get("authorNames")))
                .source(toolName)
                .build();
        dedup.put(id, card);
    }

    private String resolveMainImage(Map<?, ?> book) {
        String main = asString(book.get("mainImageUrl"));
        if (main != null && !main.isBlank()) return main;
        Object urls = book.get("imageUrls");
        if (urls instanceof List<?> list) {
            for (Object v : list) {
                String s = asString(v);
                if (s != null && !s.isBlank()) return s;
            }
        }
        return null;
    }

    private static String asString(Object v) {
        if (v == null) return null;
        return String.valueOf(v);
    }

    private static String firstNonBlank(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            String s = asString(value);
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private static Double asDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static Integer asInteger(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static List<String> asStringList(Object v) {
        if (!(v instanceof List<?> list) || list.isEmpty()) return null;
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null) continue;
            String s = String.valueOf(item).trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out.isEmpty() ? null : out;
    }
}
