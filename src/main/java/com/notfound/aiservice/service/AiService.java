package com.notfound.aiservice.service;

import com.notfound.aiservice.model.dto.request.ChatbotRequest;
import com.notfound.aiservice.model.dto.response.ChatbotResponse;

public interface AiService {
    ChatbotResponse chatbot(ChatbotRequest request);
}
