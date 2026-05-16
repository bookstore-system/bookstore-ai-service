package com.notfound.aiservice.service.impl;

import com.notfound.aiservice.client.GeminiGenerateContentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeminiClientService {

    private final GeminiGenerateContentClient geminiGenerateContentClient;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    public String ask(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return "Gemini API key chưa được cấu hình. Vui lòng set GEMINI_API_KEY để dùng chat AI.";
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        try {
            Map<String, Object> response = geminiGenerateContentClient.generateContent(model, apiKey, body);
            return extractText(response);
        } catch (Exception e) {
            return "AI service không thể kết nối Gemini: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        if (response == null) {
            return "AI service không nhận được phản hồi từ Gemini.";
        }

        Object candidatesObj = response.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) {
            return "AI service không nhận được nội dung phản hồi hợp lệ.";
        }

        Object first = candidates.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return "AI service không parse được dữ liệu phản hồi.";
        }

        Object contentObj = firstMap.get("content");
        if (!(contentObj instanceof Map<?, ?> contentMap)) {
            return "AI service không parse được content từ Gemini.";
        }

        Object partsObj = contentMap.get("parts");
        if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) {
            return "AI service không có phần trả lời text từ Gemini.";
        }

        Object part0 = parts.get(0);
        if (!(part0 instanceof Map<?, ?> partMap) || partMap.get("text") == null) {
            return "AI service không có text trong phản hồi Gemini.";
        }

        return String.valueOf(partMap.get("text"));
    }
}
