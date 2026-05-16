package com.notfound.aiservice.service;

import com.notfound.aiservice.model.dto.request.AiChatRequest;
import com.notfound.aiservice.model.dto.request.AiSearchRequest;
import com.notfound.aiservice.model.dto.request.ChatbotRequest;
import com.notfound.aiservice.model.dto.response.AiChatResponse;
import com.notfound.aiservice.model.dto.response.AiReportResponse;
import com.notfound.aiservice.model.dto.response.ChatbotResponse;

import java.util.Map;

public interface AiService {
    AiChatResponse chat(AiChatRequest request);
    ChatbotResponse chatbot(ChatbotRequest request);
    Map<String, Object> search(AiSearchRequest request);
    AiReportResponse report();
}
