package com.notfound.aiservice.agent.tool;

import com.notfound.aiservice.model.dto.request.AttachmentRequest;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Ngữ cảnh cuộc hội thoại được truyền cho tool khi gọi.
 * Tool có thể đọc userId, sessionId, attachments... nếu cần.
 */
@Data
@Builder
public class ToolContext {
    private String sessionId;
    private String userId;
    private String authorizationHeader;
    private String userMessage;
    private List<AttachmentRequest> attachments;
}
