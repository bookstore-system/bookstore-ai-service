package com.notfound.aiservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    @NotBlank
    private String message;
    private String sessionId;
}
