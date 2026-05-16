package com.notfound.aiservice.controller;

import com.notfound.aiservice.model.dto.request.ChatbotRequest;
import com.notfound.aiservice.model.dto.response.ApiResponse;
import com.notfound.aiservice.model.dto.response.ChatbotResponse;
import com.notfound.aiservice.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
@Tag(name = "Chatbot", description = "Chatbot hỗ trợ khách hàng")
public class ChatbotController {
    private final AiService aiService;

    @PostMapping("/chat")
    @Operation(summary = "Chat với AI", description = "Gửi yêu cầu ChatbotRequest và nhận phản hồi từ chatbot AI.")
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
    @Operation(summary = "Chat AI dạng text", description = "Endpoint đơn giản nhận chuỗi text và trả về phản hồi chatbot.")
    public ResponseEntity<String> simpleAi(@RequestBody String message) {
        ChatbotRequest request = new ChatbotRequest();
        request.setMessage(message);
        ChatbotResponse response = aiService.chatbot(request);
        return ResponseEntity.ok(response.getResponse());
    }
}
