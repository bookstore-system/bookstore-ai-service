package com.notfound.aiservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatbotRequest {

    @NotBlank
    private String message;

    private String sessionId;

    /**
     * Legacy field from FE. Backend does not trust this for order lookup; user
     * identity should come from gateway header X-User-Id.
     */
    private String userId;

    private List<AttachmentRequest> attachments;
}
