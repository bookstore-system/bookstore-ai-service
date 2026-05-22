package com.notfound.aiservice.controller;

import com.notfound.aiservice.agent.AiAgentService;
import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolRegistry;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.model.dto.request.AgentChatRequest;
import com.notfound.aiservice.model.dto.response.AgentChatResponse;
import com.notfound.aiservice.model.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint cho AI Agent có khả năng tool-calling.
 */
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "AI Agent", description = "AI agent tự chọn tool phù hợp để trả lời câu hỏi của user")
public class AiAgentController {

    private final AiAgentService aiAgentService;
    private final ToolRegistry toolRegistry;

    @PostMapping("/chat")
    @Operation(summary = "Chat với AI Agent (tool-calling)",
            description = "Agent tự phân tích intent, chọn tool phù hợp, gọi API/Gemini multimodal và tổng hợp phản hồi.")
    public ResponseEntity<ApiResponse<AgentChatResponse>> chat(@Valid @RequestBody AgentChatRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiAgentService.chat(request)));
    }

    @GetMapping("/tools")
    @Operation(summary = "Liệt kê các tool agent đang bật",
            description = "Trả về metadata của các tool đã đăng ký (tên, mô tả, schema).")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTools() {
        List<Map<String, Object>> result = toolRegistry.getAll().stream().map(t -> {
            ToolSchema s = t.getSchema();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", t.getName());
            item.put("description", s.getDescription());
            item.put("parameters", s.getParameters());
            item.put("required", s.getRequired());
            return item;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
