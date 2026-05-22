package com.notfound.aiservice.agent;

import com.notfound.aiservice.model.dto.request.AgentChatRequest;
import com.notfound.aiservice.model.dto.response.AgentChatResponse;

public interface AiAgentService {
    AgentChatResponse chat(AgentChatRequest request);
}
