package com.notfound.aiservice.model.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatbotResponse {
    private String response;
    private String sessionId;
}
