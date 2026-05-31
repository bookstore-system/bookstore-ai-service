package com.notfound.aiservice.agent.tool;

import java.util.Map;

/**
 * Interface chung cho mọi tool AI có thể gọi.
 *
 * Mỗi tool tự khai báo:
 *  - tên (tương ứng functionName trong Gemini)
 *  - schema (mô tả & params cho Gemini Function Calling)
 *  - logic thực thi (kết nối downstream service, AI multimodal, rule engine...)
 */
public interface Tool {

    /** Tên duy nhất, dùng làm functionName khi Gemini chọn tool. */
    String getName();

    /** Khai báo schema để Gemini biết khi nào nên gọi tool này. */
    ToolSchema getSchema();

    /** Thực thi tool với tham số do Gemini truyền vào. */
    ToolResult execute(Map<String, Object> arguments, ToolContext context);
}
