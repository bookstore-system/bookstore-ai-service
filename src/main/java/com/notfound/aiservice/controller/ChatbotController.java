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
@Tag(name = "Chatbot", description = "Chatbot AI Agent")
public class ChatbotController {

    private final AiService aiService;

    @PostMapping("/chat")
    @Operation(
            summary = "Chat with AI Agent",
            description = "Main chatbot endpoint. The agent handles intent detection, tool calls, and response synthesis."
    )
    public ResponseEntity<ApiResponse<ChatbotResponse>> chat(@Valid @RequestBody ChatbotRequest request) {
        ChatbotResponse response = aiService.chatbot(request);
        return ResponseEntity.ok(
                ApiResponse.<ChatbotResponse>builder()
                        .code(1000)
                        .message("Gui tin nhan thanh cong")
                        .result(response)
                        .build());
    }
}
