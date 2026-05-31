package com.notfound.aiservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Request cho endpoint AI Agent. Khác với ChatbotRequest cũ, request này
 * có thêm userId để các tool như recommendation/orderLookup biết user nào
 * đang tương tác.
 */
@Data
public class AgentChatRequest {

    @NotBlank
    private String message;

    private String sessionId;
    private String userId;
    private String authorizationHeader;
    private List<AttachmentRequest> attachments;
}
