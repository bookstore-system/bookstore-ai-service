package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CS-15: Phát hiện & xử lý câu hỏi ngoài phạm vi.
 *
 * Bản thân tool này hoạt động như lớp filter pre-flight, có thể được gọi
 * bởi orchestrator trước khi gọi Gemini chính. Logic dùng từ điển blacklist
 * + scope keywords để giảm chi phí API.
 */
@Slf4j
@Component
public class GuardrailTool implements Tool {

    public static final String NAME = "guardrailTool";

    private static final List<String> OUT_OF_SCOPE_KEYWORDS = List.of(
            "nghị luận", "văn nghị luận", "làm bài tập", "viết hộ", "viết giúp tôi bài",
            "code hộ", "viết code", "hack", "mã độc", "malware", "virus",
            "chính trị", "bầu cử", "đảng",
            "thuốc", "khám bệnh", "chẩn đoán",
            "đầu tư chứng khoán", "lô đề", "cá độ"
    );

    private static final List<String> BOOKSTORE_SCOPE_KEYWORDS = List.of(
            "sách", "tác giả", "isbn", "nhà xuất bản", "nxb",
            "đơn hàng", "khuyến mãi", "giảm giá", "mã giảm giá",
            "đánh giá", "review", "rating", "thể loại", "danh mục",
            "đặt mua", "giỏ hàng", "tồn kho", "stock", "shipping",
            "gợi ý", "so sánh", "tóm tắt"
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Kiểm tra câu hỏi của user có nằm trong phạm vi nhà sách hay không.")
                .parameter("message", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("Câu hỏi gốc của user.")
                        .build())
                .requiredParameter("message")
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        String message = ArgUtil.getString(arguments, "message", "");
        return ToolResult.ok(NAME, classify(message));
    }

    /** API dùng nội bộ cho orchestrator (pre-flight). */
    public Map<String, Object> classify(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean hasOutOfScopeHit = OUT_OF_SCOPE_KEYWORDS.stream().anyMatch(lower::contains);
        boolean hasInScopeHit = BOOKSTORE_SCOPE_KEYWORDS.stream().anyMatch(lower::contains);

        String decision;
        if (hasOutOfScopeHit && !hasInScopeHit) {
            decision = "OUT_OF_SCOPE";
        } else if (!hasInScopeHit && lower.length() > 0 && containsCommandIntent(lower)) {
            decision = "LIKELY_OUT_OF_SCOPE";
        } else {
            decision = "IN_SCOPE";
        }

        String suggestion = decision.equals("OUT_OF_SCOPE")
                ? "Xin lỗi, tôi là trợ lý ảo của Nhà sách. Tôi chỉ có thể giúp bạn tìm kiếm và tư vấn về sách. Bạn có muốn tôi tìm các đầu sách liên quan tới chủ đề bạn quan tâm không?"
                : null;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("decision", decision);
        data.put("inScopeHit", hasInScopeHit);
        data.put("outOfScopeHit", hasOutOfScopeHit);
        data.put("suggestedResponse", suggestion);
        return data;
    }

    private boolean containsCommandIntent(String lower) {
        return lower.contains("viết giúp")
                || lower.contains("làm hộ")
                || lower.contains("viết hộ")
                || lower.contains("code hộ")
                || lower.contains("dịch hộ");
    }
}
