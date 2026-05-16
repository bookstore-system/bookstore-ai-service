package com.notfound.aiservice.model.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ChatbotRequest {
    private String message;
    private String sessionId;
    private List<AttachmentRequest> attachments;
}
