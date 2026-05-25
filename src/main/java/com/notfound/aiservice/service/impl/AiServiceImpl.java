package com.notfound.aiservice.service.impl;

import com.notfound.aiservice.agent.AiAgentService;
import com.notfound.aiservice.model.dto.request.AgentChatRequest;
import com.notfound.aiservice.model.dto.request.ChatbotRequest;
import com.notfound.aiservice.model.dto.response.AgentChatResponse;
import com.notfound.aiservice.model.dto.response.ChatbotResponse;
import com.notfound.aiservice.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final AiAgentService aiAgentService;

    @Override
    public ChatbotResponse chatbot(ChatbotRequest request) {
        AgentChatResponse agentResponse = aiAgentService.chat(toAgentRequest(request));
        return ChatbotResponse.builder()
                .response(agentResponse.getResponse())
                .sessionId(agentResponse.getSessionId())
                .intent(agentResponse.getIntent())
                .toolCalls(mapChatbotTraces(agentResponse.getToolCalls()))
                .books(agentResponse.getBooks() == null ? List.of() : agentResponse.getBooks())
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

    private List<ChatbotResponse.ToolCallTrace> mapChatbotTraces(List<AgentChatResponse.ToolCallTrace> traces) {
        if (traces == null) {
            return List.of();
        }
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
}
