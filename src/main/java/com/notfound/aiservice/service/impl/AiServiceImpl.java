package com.notfound.aiservice.service.impl;

import com.notfound.aiservice.agent.AiAgentService;
import com.notfound.aiservice.client.BookServiceClient;
import com.notfound.aiservice.client.OrderServiceClient;
import com.notfound.aiservice.model.dto.request.AgentChatRequest;
import com.notfound.aiservice.model.dto.request.AiChatRequest;
import com.notfound.aiservice.model.dto.request.AiSearchRequest;
import com.notfound.aiservice.model.dto.request.ChatbotRequest;
import com.notfound.aiservice.model.dto.response.AgentChatResponse;
import com.notfound.aiservice.model.dto.response.AiChatResponse;
import com.notfound.aiservice.model.dto.response.AiReportResponse;
import com.notfound.aiservice.model.dto.response.ChatbotResponse;
import com.notfound.aiservice.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Sau khi gộp agent vào chat, mọi luồng "chat" đều đi qua {@link AiAgentService}
 * để tận dụng tool-calling. Service này chỉ còn trách nhiệm:
 *  - Adapt request/response DTO giữa controller cũ (chat/chatbot) và agent.
 *  - Giữ các tác vụ không phải chat: search trực tiếp, report đơn hàng.
 */
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final AiAgentService aiAgentService;
    private final GeminiClientService geminiClientService;
    private final BookServiceClient bookServiceClient;
    private final OrderServiceClient orderServiceClient;

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        AgentChatResponse agentResponse = aiAgentService.chat(toAgentRequest(request));
        return AiChatResponse.builder()
                .response(agentResponse.getResponse())
                .sessionId(agentResponse.getSessionId())
                .intent(agentResponse.getIntent())
                .toolCalls(mapAiTraces(agentResponse.getToolCalls()))
                .build();
    }

    @Override
    public ChatbotResponse chatbot(ChatbotRequest request) {
        AgentChatResponse agentResponse = aiAgentService.chat(toAgentRequest(request));
        return ChatbotResponse.builder()
                .response(agentResponse.getResponse())
                .sessionId(agentResponse.getSessionId())
                .intent(agentResponse.getIntent())
                .toolCalls(mapChatbotTraces(agentResponse.getToolCalls()))
                .build();
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

    private AgentChatRequest toAgentRequest(ChatbotRequest req) {
        AgentChatRequest agent = new AgentChatRequest();
        agent.setMessage(req.getMessage());
        agent.setSessionId(req.getSessionId());
        agent.setUserId(req.getUserId());
        agent.setAttachments(req.getAttachments());
        return agent;
    }

    private AgentChatRequest toAgentRequest(AiChatRequest req) {
        AgentChatRequest agent = new AgentChatRequest();
        agent.setMessage(req.getMessage());
        agent.setSessionId(req.getSessionId());
        agent.setUserId(req.getUserId());
        agent.setAttachments(req.getAttachments());
        return agent;
    }

    private List<ChatbotResponse.ToolCallTrace> mapChatbotTraces(List<AgentChatResponse.ToolCallTrace> traces) {
        if (traces == null) return List.of();
        return traces.stream()
                .map(t -> ChatbotResponse.ToolCallTrace.builder()
                        .toolName(t.getToolName())
                        .arguments(t.getArguments())
                        .success(t.isSuccess())
                        .errorMessage(t.getErrorMessage())
                        .data(t.getData())
                        .build())
                .toList();
    }

    private List<AiChatResponse.ToolCallTrace> mapAiTraces(List<AgentChatResponse.ToolCallTrace> traces) {
        if (traces == null) return List.of();
        return traces.stream()
                .map(t -> AiChatResponse.ToolCallTrace.builder()
                        .toolName(t.getToolName())
                        .arguments(t.getArguments())
                        .success(t.isSuccess())
                        .errorMessage(t.getErrorMessage())
                        .data(t.getData())
                        .build())
                .toList();
    }
}
