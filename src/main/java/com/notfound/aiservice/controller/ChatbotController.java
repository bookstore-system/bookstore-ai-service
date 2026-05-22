package com.notfound.aiservice.controller;

import com.notfound.aiservice.agent.tool.ToolRegistry;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.model.dto.request.ChatbotRequest;
import com.notfound.aiservice.model.dto.response.ApiResponse;
import com.notfound.aiservice.model.dto.response.ChatbotResponse;
import com.notfound.aiservice.service.AiService;
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
 * Chatbot controller — endpoint chat duy nhất của service.
 *
 * Sau khi gộp với AI Agent, mọi request chat đều đi qua tool-calling loop:
 *  - Phân tích intent.
 *  - Tự chọn & gọi tool (search, recommendation, order lookup, multimodal, ...).
 *  - Tổng hợp phản hồi tiếng Việt + trả kèm trace tool calls cho FE.
 */
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
@Tag(name = "Chatbot", description = "Chatbot AI Agent (tool-calling) hỗ trợ khách hàng")
public class ChatbotController {

    private final AiService aiService;
    private final ToolRegistry toolRegistry;

    @PostMapping("/chat")
    @Operation(summary = "Chat với AI Agent",
            description = "Endpoint chat chính. Agent tự phân tích intent, chọn tool phù hợp "
                    + "(search, recommendation, order lookup, multimodal image, ...), gọi downstream "
                    + "service rồi tổng hợp phản hồi. Response kèm trace `toolCalls` để FE hiển thị.")
    public ResponseEntity<ApiResponse<ChatbotResponse>> chat(@Valid @RequestBody ChatbotRequest request) {
        ChatbotResponse response = aiService.chatbot(request);
        return ResponseEntity.ok(
                ApiResponse.<ChatbotResponse>builder()
                        .code(1000)
                        .message("Gửi tin nhắn thành công")
                        .result(response)
                        .build());
    }

    @PostMapping("/ai")
    @Operation(summary = "Chat AI dạng text",
            description = "Endpoint đơn giản nhận chuỗi text và trả về phản hồi chatbot (vẫn dùng agent bên dưới, "
                    + "nhưng không expose toolCalls).")
    public ResponseEntity<String> simpleAi(@RequestBody String message) {
        ChatbotRequest request = new ChatbotRequest();
        request.setMessage(message);
        ChatbotResponse response = aiService.chatbot(request);
        return ResponseEntity.ok(response.getResponse());
    }

    @GetMapping("/tools")
    @Operation(summary = "Liệt kê các tool agent đang bật",
            description = "Trả về metadata các tool đã đăng ký (tên, mô tả, schema) "
                    + "để FE/devtool biết chatbot có thể làm gì.")
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
