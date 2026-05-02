package com.notfound.aiservice.controller;

import com.notfound.aiservice.model.dto.request.AiChatRequest;
import com.notfound.aiservice.model.dto.request.AiSearchRequest;
import com.notfound.aiservice.model.dto.response.AiChatResponse;
import com.notfound.aiservice.model.dto.response.AiReportResponse;
import com.notfound.aiservice.model.dto.response.ApiResponse;
import com.notfound.aiservice.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@RequestBody @Valid AiChatRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiService.chat(request)));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> search(@RequestBody @Valid AiSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiService.search(request)));
    }

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<AiReportResponse>> report() {
        return ResponseEntity.ok(ApiResponse.success(aiService.report()));
    }
}
