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
                .description("Nhan dien sach tu anh bia user gui len va tim sach tuong ung trong nha sach.")
                .parameter("hint", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("Cau hoi hoac goi y kem theo anh.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        if (context == null || context.getAttachments() == null || context.getAttachments().isEmpty()) {
            return ToolResult.fail(NAME, "Khong co anh dinh kem. User can gui kem anh bia sach.");
        }

        List<AttachmentRequest> imageAttachments = context.getAttachments().stream()
                .filter(a -> a != null
                        && a.getType() != null
                        && a.getType().toLowerCase().startsWith("image"))
                .toList();
        if (imageAttachments.isEmpty()) {
            return ToolResult.fail(NAME, "Attachment hien tai khong phai anh.");
        }

        try {
            log.info(
                    "imageScannerTool received images={}, urlKinds={}",
                    imageAttachments.size(),
                    imageAttachments.stream().map(a -> urlKind(a.getUrl())).toList()
            );

            String hint = ArgUtil.getString(
                    arguments,
                    "hint",
                    "Hay trich xuat ten sach, tac gia, nha xuat ban tu anh bia."
            );
            String extractionPrompt = """
                    Ban la module OCR bia sach. Phan tich anh va chi tra ve JSON dung format:
                    {
                      "title": "...",
                      "authors": ["..."],
                      "publisher": "..."
                    }
                    Neu khong nhan dien duoc truong nao, de rong.
                    Yeu cau them: %s
                    """.formatted(hint);

            String rawExtraction = aiModelClient.askMultimodal(extractionPrompt, imageAttachments);
            if (rawExtraction != null && rawExtraction.startsWith("MULTIMODAL_UNSUPPORTED")) {
                return ToolResult.fail(NAME, rawExtraction);
            }

            String inferredTitle = guessTitleFromJson(rawExtraction);
            log.info(
                    "imageScannerTool extraction inferredTitle='{}', rawPreview='{}'",
                    inferredTitle,
                    preview(rawExtraction)
            );

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
            return ToolResult.fail(NAME, "Khong xu ly duoc anh: " + e.getMessage());
        }
    }

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

    private String urlKind(String url) {
        if (url == null || url.isBlank()) return "empty";
        if (url.startsWith("data:")) return "data-url";
        if (url.startsWith("http")) return "http-url";
        if (url.startsWith("blob:")) return "blob-url-unsupported";
        return "other";
    }

    private String preview(String text) {
        if (text == null) return "";
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 180 ? compact : compact.substring(0, 180) + "...";
    }
}
