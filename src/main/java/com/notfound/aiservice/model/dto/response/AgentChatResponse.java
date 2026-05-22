package com.notfound.aiservice.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response cho AI Agent. Ngoài câu trả lời cuối cùng, expose cả các tool đã
 * dùng và dữ liệu thô để FE có thể hiển thị card sản phẩm/đơn hàng/khuyến mãi.
 */
@Data
@Builder
public class AgentChatResponse {
    private String response;
    private String sessionId;
    private String intent;
    private List<ToolCallTrace> toolCalls;

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
