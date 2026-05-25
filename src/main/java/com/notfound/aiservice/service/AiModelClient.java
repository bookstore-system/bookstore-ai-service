package com.notfound.aiservice.service;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.model.dto.request.AttachmentRequest;
import com.notfound.aiservice.model.dto.response.AgentChatResponse;

import java.util.List;
import java.util.Collection;

public interface AiModelClient {
    String getModel();

    boolean isConfigured();

    String ask(String prompt);

    String askMultimodal(String prompt, List<AttachmentRequest> attachments);

    String chatWithTools(
            String systemPrompt,
            List<String> historyMessages,
            String userMessage,
            Collection<Tool> tools,
            ToolContext context,
            List<AgentChatResponse.ToolCallTrace> trace
    );
}
