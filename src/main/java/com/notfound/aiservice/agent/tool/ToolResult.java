package com.notfound.aiservice.agent.tool;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Kết quả trả về sau khi tool được thực thi.
 * `data` sẽ được chuyển ngược lại cho Gemini làm function-response.
 */
@Data
@Builder
public class ToolResult {
    private String toolName;
    private boolean success;
    private String errorMessage;
    private Map<String, Object> data;

    public static ToolResult ok(String toolName, Map<String, Object> data) {
        return ToolResult.builder().toolName(toolName).success(true).data(data).build();
    }

    public static ToolResult fail(String toolName, String message) {
        return ToolResult.builder().toolName(toolName).success(false).errorMessage(message).build();
    }
}
