package com.notfound.aiservice.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response của endpoint chat. Vì chat đã được nâng cấp thành AI Agent có
 * tool-calling, response trả thêm:
 *  - intent: tool đầu tiên agent chọn (hoặc DIRECT_ANSWER / OUT_OF_SCOPE).
 *  - toolCalls: trace các tool đã thực thi (tên, tham số, success, data).
 *  - books: card sách chuẩn hoá để FE render trực tiếp (không cần parse trace).
 */
@Data
@Builder
public class ChatbotResponse {
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
