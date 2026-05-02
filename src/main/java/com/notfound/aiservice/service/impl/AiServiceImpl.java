package com.notfound.aiservice.service.impl;

import com.notfound.aiservice.client.BookServiceClient;
import com.notfound.aiservice.client.OrderServiceClient;
import com.notfound.aiservice.model.dto.request.AiChatRequest;
import com.notfound.aiservice.model.dto.request.AiSearchRequest;
import com.notfound.aiservice.model.dto.response.AiChatResponse;
import com.notfound.aiservice.model.dto.response.AiReportResponse;
import com.notfound.aiservice.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {
    private final GeminiClientService geminiClientService;
    private final BookServiceClient bookServiceClient;
    private final OrderServiceClient orderServiceClient;

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getSessionId();

        String prompt = "Bạn là trợ lý AI cho hệ thống bán sách. Trả lời ngắn gọn, thân thiện bằng tiếng Việt.\n"
                + "Câu hỏi người dùng: " + request.getMessage();

        String answer = geminiClientService.ask(prompt);
        return AiChatResponse.builder().response(answer).sessionId(sessionId).build();
    }

    @Override
    public Map<String, Object> search(AiSearchRequest request) {
        int page = request.getPage() == null || request.getPage() < 0 ? 0 : request.getPage();
        int size = request.getSize() == null || request.getSize() <= 0 ? 10 : request.getSize();
        return bookServiceClient.searchBooks(page, size, request.getKeyword());
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
}
