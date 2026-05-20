package com.notfound.aiservice.service.impl;

import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.GeminiGenerateContentClient;
import com.notfound.aiservice.model.dto.request.AttachmentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lớp tích hợp với Gemini API:
 *  - ask(prompt): hỏi text đơn giản (giữ tương thích cũ)
 *  - askMultimodal: gửi kèm ảnh (CS-13)
 *  - generateWithTools: gửi prompt + tool schema (function calling) → trả về raw response
 *
 * Service tách phần xây dựng request và parse response để Orchestrator
 * có thể chạy vòng lặp nhiều bước (multi-step tool use).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiClientService {

    private final GeminiGenerateContentClient geminiGenerateContentClient;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    public String getModel() {
        return model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Helper text-only đơn giản. */
    public String ask(String prompt) {
        if (!isConfigured()) {
            return "Gemini API key chưa được cấu hình. Vui lòng set GEMINI_API_KEY để dùng chat AI.";
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))
                )
        );
        try {
            Map<String, Object> response = geminiGenerateContentClient.generateContent(model, apiKey, body);
            return extractText(response);
        } catch (Exception e) {
            log.warn("Gemini ask failed: {}", e.getMessage());
            return "AI service không thể kết nối Gemini: " + e.getMessage();
        }
    }

    /** Gọi Gemini với ảnh đính kèm. */
    public String askMultimodal(String prompt, List<AttachmentRequest> attachments) {
        if (!isConfigured()) {
            return "Gemini API key chưa được cấu hình.";
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));
        if (attachments != null) {
            for (AttachmentRequest a : attachments) {
                if (a == null || a.getType() == null) continue;
                if (!a.getType().toLowerCase().startsWith("image")) continue;
                Map<String, Object> inlineData = buildInlineImagePart(a);
                if (inlineData != null) {
                    parts.add(Map.of("inlineData", inlineData));
                }
            }
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", parts))
        );
        try {
            Map<String, Object> response = geminiGenerateContentClient.generateContent(model, apiKey, body);
            return extractText(response);
        } catch (Exception e) {
            log.warn("Gemini multimodal failed: {}", e.getMessage());
            return "AI service không xử lý được ảnh: " + e.getMessage();
        }
    }

    /**
     * Gửi prompt + tool schema cho Gemini và trả về raw response.
     * Caller (Orchestrator) tự parse phần `functionCall` để biết tool nào cần gọi.
     */
    public Map<String, Object> generateWithTools(
            List<Map<String, Object>> contents,
            Collection<ToolSchema> tools
    ) {
        if (!isConfigured()) {
            return Map.of("error", "Gemini API key chưa được cấu hình.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", contents);

        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> functionDeclarations = new ArrayList<>();
            for (ToolSchema schema : tools) {
                functionDeclarations.add(toFunctionDeclaration(schema));
            }
            body.put("tools", List.of(Map.of("functionDeclarations", functionDeclarations)));
            body.put("toolConfig", Map.of(
                    "functionCallingConfig", Map.of("mode", "AUTO")
            ));
        }

        try {
            return geminiGenerateContentClient.generateContent(model, apiKey, body);
        } catch (Exception e) {
            log.warn("Gemini generateWithTools failed: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /** Convert ToolSchema → Gemini functionDeclaration JSON. */
    private Map<String, Object> toFunctionDeclaration(ToolSchema schema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (schema.getParameters() != null) {
            schema.getParameters().forEach((k, v) -> properties.put(k, toJsonSchema(v)));
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            parameters.put("required", schema.getRequired());
        }

        Map<String, Object> decl = new LinkedHashMap<>();
        decl.put("name", schema.getName());
        decl.put("description", schema.getDescription());
        decl.put("parameters", parameters);
        return decl;
    }

    private Map<String, Object> toJsonSchema(ToolSchema.ParameterSchema p) {
        Map<String, Object> node = new LinkedHashMap<>();
        if (p.getType() != null) node.put("type", p.getType());
        if (p.getDescription() != null) node.put("description", p.getDescription());
        if (p.getEnumValues() != null) node.put("enum", p.getEnumValues());
        if ("array".equalsIgnoreCase(p.getType()) && p.getItems() != null) {
            node.put("items", toJsonSchema(p.getItems()));
        }
        return node;
    }

    /** Lấy ảnh thành base64 (giới hạn 4MB, đủ cho ảnh bìa sách). */
    private Map<String, Object> buildInlineImagePart(AttachmentRequest a) {
        try {
            String mime = "image/jpeg";
            if (a.getType() != null && a.getType().contains("/")) {
                mime = a.getType();
            } else if (a.getName() != null && a.getName().toLowerCase().endsWith(".png")) {
                mime = "image/png";
            }

            String base64Data = null;
            if (a.getUrl() != null && a.getUrl().startsWith("data:")) {
                int commaIdx = a.getUrl().indexOf(',');
                if (commaIdx > 0) {
                    base64Data = a.getUrl().substring(commaIdx + 1);
                }
            } else if (a.getUrl() != null && a.getUrl().startsWith("http")) {
                byte[] bytes = fetchBytes(a.getUrl());
                if (bytes != null) base64Data = Base64.getEncoder().encodeToString(bytes);
            }
            if (base64Data == null) return null;
            return Map.of("mimeType", mime, "data", base64Data);
        } catch (Exception e) {
            log.debug("buildInlineImagePart failed: {}", e.getMessage());
            return null;
        }
    }

    private byte[] fetchBytes(String url) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return resp.body();
            }
        } catch (Exception e) {
            log.debug("fetchBytes failed: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public String extractText(Map<String, Object> response) {
        if (response == null) return "";
        Object candidatesObj = response.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) return "";
        Object first = candidates.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) return "";
        Object contentObj = firstMap.get("content");
        if (!(contentObj instanceof Map<?, ?> contentMap)) return "";
        Object partsObj = contentMap.get("parts");
        if (!(partsObj instanceof List<?> parts)) return "";
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            if (p instanceof Map<?, ?> pm) {
                Object text = pm.get("text");
                if (text != null) sb.append(text);
            }
        }
        return sb.toString();
    }
}
