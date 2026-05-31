package com.notfound.aiservice.controller;

import com.notfound.aiservice.model.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Statistics", description = "AI statistics endpoints")
public class AiStatisticsController {

    @PostMapping("/statistics")
    @Operation(
            summary = "AI statistics",
            description = "Placeholder endpoint for the AI statistics feature. Business logic will be added later."
    )
    public ResponseEntity<ApiResponse<Void>> statistics() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.<Void>builder()
                        .code(501)
                        .message("AI statistics endpoint is available. Logic will be implemented later.")
                        .result(null)
                        .build());
    }
}
