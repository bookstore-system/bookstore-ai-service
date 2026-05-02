package com.notfound.aiservice.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AiReportResponse {
    private String summary;
    private List<Map<String, Object>> orderStats;
}
