package com.notfound.aiservice.agent.tool;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;
import java.util.Map;

/**
 * Mô tả khai báo (schema) của một tool dùng cho Gemini Function Calling.
 * Cấu trúc tương thích với phần `functionDeclarations` của Gemini API.
 */
@Data
@Builder
public class ToolSchema {
    private String name;
    private String description;
    @Singular("parameter")
    private Map<String, ParameterSchema> parameters;
    @Singular("requiredParameter")
    private List<String> required;

    @Data
    @Builder
    public static class ParameterSchema {
        private String type;
        private String description;
        private List<String> enumValues;
        private ParameterSchema items;
    }
}
