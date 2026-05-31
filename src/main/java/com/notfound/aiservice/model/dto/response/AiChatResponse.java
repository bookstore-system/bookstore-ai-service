package com.notfound.aiservice.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response của endpoint AI chat. Sau khi gộp chat vào AI Agent, response trả
 * thêm intent, trace các tool đã gọi và danh sách card sách chuẩn hoá.
 */
@Data
@Builder
public class AiChatResponse {
    private String response;
    private String sessionId;
    private String intent;
    private ResponseAction action;
    private List<ToolCallTrace> toolCalls;
    private List<BookCard> books;

    @Data
    @Builder
    public static class ToolCallTrace {
        private String toolName;
        private Map<String, Object> arguments;
        private boolean success;
        private String errorMessage;
        private Map<String, Object> data;
    }
}
