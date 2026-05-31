package com.notfound.aiservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiSearchRequest {
    @NotBlank
    private String keyword;
    private Integer page = 0;
    private Integer size = 10;
}
