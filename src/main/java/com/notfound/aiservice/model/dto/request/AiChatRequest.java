package com.notfound.aiservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AiChatRequest {

    @NotBlank
    private String message;

    private String sessionId;

    /** UUID của user — dùng cho các tool agent cần ngữ cảnh người dùng. */
    private String userId;

    private List<AttachmentRequest> attachments;
}
