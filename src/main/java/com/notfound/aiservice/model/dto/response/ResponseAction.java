package com.notfound.aiservice.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ResponseAction {
    private String type;
    private Map<String, Object> payload;
}
