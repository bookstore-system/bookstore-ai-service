package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.PromotionServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CS-14: Gợi ý mã khuyến mãi.
 * Lấy promotion active rồi chạy rule engine nhẹ:
 *  - orderValue >= minOrderValue (nếu có)
 *  - promotion đang active
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionTool implements Tool {

    public static final String NAME = "promotionTool";

    private final PromotionServiceClient promotionServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Gợi ý mã khuyến mãi dựa trên giá trị đơn hàng hiện tại của user.")
                .parameter("orderValue", ToolSchema.ParameterSchema.builder()
                        .type("number")
                        .description("Tổng giá trị đơn hàng hiện tại (VNĐ).")
                        .build())
                .requiredParameter("orderValue")
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        Double orderValue = ArgUtil.getDouble(arguments, "orderValue", 0.0);
        if (orderValue == null) orderValue = 0.0;

        try {
            Map<String, Object> activeResponse = promotionServiceClient.getActivePromotions();
            List<Map<String, Object>> all = extractList(activeResponse);
            List<Map<String, Object>> eligible = filterByRule(all, orderValue);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderValue", orderValue);
            data.put("eligiblePromotions", eligible);
            data.put("totalEligible", eligible.size());
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("promotionTool failed: {}", e.getMessage());
            return ToolResult.ok(NAME, Map.of(
                    "orderValue", orderValue,
                    "eligiblePromotions", List.of(),
                    "totalEligible", 0,
                    "warning", "Promotion service không khả dụng, không có mã giảm giá nào."
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Map<String, Object> response) {
        if (response == null) return List.of();
        Object result = response.get("result");
        if (result instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        if (result instanceof Map<?, ?> map) {
            Object content = ((Map<String, Object>) map).get("content");
            if (content instanceof List<?> contentList) {
                return (List<Map<String, Object>>) contentList;
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> filterByRule(List<Map<String, Object>> all, double orderValue) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> promo : all) {
            Object status = promo.get("status");
            if (status != null && !"ACTIVE".equalsIgnoreCase(String.valueOf(status))) {
                continue;
            }
            Object minOrderValue = promo.get("minOrderValue");
            double min = minOrderValue instanceof Number n ? n.doubleValue() : 0.0;
            if (orderValue >= min) {
                result.add(promo);
            }
        }
        return result;
    }
}
