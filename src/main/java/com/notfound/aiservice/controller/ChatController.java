package com.notfound.aiservice.controller;

import com.notfound.aiservice.model.dto.request.ChatbotRequest;
import com.notfound.aiservice.model.dto.response.ChatbotResponse;
import com.notfound.aiservice.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Chat AI đơn giản (legacy)")
public class ChatController {
    private final AiService aiService;

    @PostMapping("/ai")
    @Operation(summary = "Gửi tin nhắn AI alias", description = "Endpoint tương thích cũ nhận text và trả về phản hồi chatbot.")
    public ResponseEntity<String> ai(@RequestBody String message) {
        ChatbotRequest request = new ChatbotRequest();
        request.setMessage(message);
        ChatbotResponse response = aiService.chatbot(request);
        return ResponseEntity.ok(response.getResponse());
    }
}
