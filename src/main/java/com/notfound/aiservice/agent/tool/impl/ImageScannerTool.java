package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.BookServiceClient;
import com.notfound.aiservice.model.dto.request.AttachmentRequest;
import com.notfound.aiservice.service.AiModelClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CS-13: Đọc ảnh bìa sách đa phương thức.
 * Pipeline:
 *  1. Lấy ảnh từ ToolContext.attachments (type=image, có url hoặc base64)
 *  2. Gọi Gemini multimodal để trích xuất tên sách, tác giả, NXB
 *  3. Dùng kết quả gọi sang Book-Service để tra giá + tồn kho
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageScannerTool implements Tool {

    public static final String NAME = "imageScannerTool";

    private final AiModelClient aiModelClient;
    private final BookServiceClient bookServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Nhận diện sách từ ảnh bìa user gửi lên (multimodal). Dùng khi user gửi attachment ảnh và hỏi 'cuốn này bên mình có bán không'.")
                .parameter("hint", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("Câu hỏi/hint kèm theo ảnh để gợi cách trích xuất.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        if (context == null || context.getAttachments() == null || context.getAttachments().isEmpty()) {
            return ToolResult.fail(NAME, "Không có ảnh đính kèm. User cần gửi kèm ảnh bìa sách.");
        }

        List<AttachmentRequest> imageAttachments = context.getAttachments().stream()
                .filter(a -> a != null && a.getType() != null
                        && (a.getType().toLowerCase().startsWith("image")
                            || a.getType().equalsIgnoreCase("image")))
                .toList();

        if (imageAttachments.isEmpty()) {
            return ToolResult.fail(NAME, "Attachment hiện tại không phải ảnh.");
        }

        try {
            String hint = ArgUtil.getString(arguments, "hint", "Hãy trích xuất tên sách, tác giả, NXB từ ảnh bìa.");
            String extractionPrompt = """
                    Bạn là module OCR bìa sách. Phân tích ảnh và trả về JSON đúng format:
                    {
                      "title": "...",
                      "authors": ["..."],
                      "publisher": "..."
                    }
                    Nếu không nhận diện được trường nào, để rỗng.
                    Yêu cầu thêm: %s
                    """.formatted(hint);

            String rawExtraction = aiModelClient.askMultimodal(extractionPrompt, imageAttachments);

            String inferredTitle = guessTitleFromJson(rawExtraction);

            Map<String, Object> bookInfo = null;
            if (!inferredTitle.isBlank()) {
                try {
                    bookInfo = bookServiceClient.searchBooks(inferredTitle, 0, 3);
                } catch (Exception e) {
                    log.debug("searchBooks for inferred title failed: {}", e.getMessage());
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rawExtraction", rawExtraction);
            data.put("inferredTitle", inferredTitle);
            data.put("matchedBooks", bookInfo);
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("imageScannerTool failed", e);
            return ToolResult.fail(NAME, "Không xử lý được ảnh: " + e.getMessage());
        }
    }

    /** Bóc field "title" từ JSON text Gemini trả về (parser nhẹ, không cần Jackson). */
    private String guessTitleFromJson(String text) {
        if (text == null) return "";
        int titleIdx = text.toLowerCase().indexOf("\"title\"");
        if (titleIdx < 0) return "";
        int colon = text.indexOf(':', titleIdx);
        if (colon < 0) return "";
        int firstQuote = text.indexOf('"', colon + 1);
        if (firstQuote < 0) return "";
        int secondQuote = text.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return "";
        return text.substring(firstQuote + 1, secondQuote).trim();
    }
}
