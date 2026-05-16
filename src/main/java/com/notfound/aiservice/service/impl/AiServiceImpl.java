package com.notfound.aiservice.service.impl;

import com.notfound.aiservice.client.BookServiceClient;
import com.notfound.aiservice.client.OrderServiceClient;
import com.notfound.aiservice.model.dto.request.AiChatRequest;
import com.notfound.aiservice.model.dto.request.AiSearchRequest;
import com.notfound.aiservice.model.dto.request.AttachmentRequest;
import com.notfound.aiservice.model.dto.request.ChatbotRequest;
import com.notfound.aiservice.model.dto.response.AiChatResponse;
import com.notfound.aiservice.model.dto.response.AiReportResponse;
import com.notfound.aiservice.model.dto.response.ChatbotResponse;
import com.notfound.aiservice.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {
    private final GeminiClientService geminiClientService;
    private final BookServiceClient bookServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final Map<String, List<String>> chatHistoryBySession = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_MESSAGES = 20;

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getSessionId();

        String prompt = buildPromptWithHistory(sessionId, request.getMessage(), null);
        String answer = geminiClientService.ask(prompt);
        updateHistory(sessionId, request.getMessage(), answer);
        return AiChatResponse.builder().response(answer).sessionId(sessionId).build();
    }

    @Override
    public ChatbotResponse chatbot(ChatbotRequest request) {
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getSessionId();
        String message = request.getMessage() == null ? "" : request.getMessage();

        String prompt = buildPromptWithHistory(sessionId, message, request.getAttachments());
        String answer = geminiClientService.ask(prompt);
        updateHistory(sessionId, message, answer);
        return ChatbotResponse.builder().response(answer).sessionId(sessionId).build();
    }

    @Override
    public Map<String, Object> search(AiSearchRequest request) {
        int page = request.getPage() == null || request.getPage() < 0 ? 0 : request.getPage();
        int size = request.getSize() == null || request.getSize() <= 0 ? 10 : request.getSize();
        return bookServiceClient.searchBooks(request.getKeyword(), page, size);
    }

    @Override
    public AiReportResponse report() {
        Map<String, Object> raw = orderServiceClient.getOrderStats();
        List<Map<String, Object>> stats = List.of(raw);
        String summary = geminiClientService.ask(
                "Hãy tóm tắt nhanh số liệu đơn hàng sau bằng tiếng Việt trong 3-4 câu: " + raw
        );
        return AiReportResponse.builder()
                .summary(summary)
                .orderStats(stats)
                .build();
    }

    private String buildPromptWithHistory(String sessionId, String message, List<AttachmentRequest> attachments) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là BookBot cho hệ thống bán sách. ")
                .append("Trả lời bằng tiếng Việt, thân thiện, không markdown, không bịa thông tin.\n");

        List<String> history = chatHistoryBySession.getOrDefault(sessionId, List.of());
        if (!history.isEmpty()) {
            prompt.append("Lịch sử hội thoại gần đây:\n");
            for (String line : history) {
                prompt.append(line).append('\n');
            }
        }

        prompt.append("Tin nhắn hiện tại: ").append(message).append('\n');

        if (attachments != null && !attachments.isEmpty()) {
            prompt.append("Đính kèm:\n");
            for (AttachmentRequest attachment : attachments) {
                if (attachment == null) {
                    continue;
                }
                prompt.append("- type=").append(attachment.getType());
                if (attachment.getName() != null) {
                    prompt.append(", name=").append(attachment.getName());
                }
                if (attachment.getUrl() != null) {
                    prompt.append(", url=").append(attachment.getUrl());
                }
                if (attachment.getLocation() != null) {
                    prompt.append(", location=").append(
                            attachment.getLocation().getAddress() != null
                                    ? attachment.getLocation().getAddress()
                                    : (attachment.getLocation().getLat() + "," + attachment.getLocation().getLng())
                    );
                }
                prompt.append('\n');
            }
        }
        return prompt.toString();
    }

    private void updateHistory(String sessionId, String userMessage, String aiMessage) {
        List<String> history = chatHistoryBySession.computeIfAbsent(sessionId, key -> new ArrayList<>());
        history.add("User: " + userMessage);
        history.add("Assistant: " + aiMessage);
        while (history.size() > MAX_HISTORY_MESSAGES) {
            history.remove(0);
        }
    }
}
