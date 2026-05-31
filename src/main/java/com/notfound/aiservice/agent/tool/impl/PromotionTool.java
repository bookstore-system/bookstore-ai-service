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
 * CS-14: Goi y ma khuyen mai.
 * Neu user chua co gia tri don hang, tra ve danh sach voucher dang active.
 * Neu user co gia tri don hang, loc them theo minOrderValue.
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
                .description("Lay danh sach voucher/ma khuyen mai dang active. orderValue la tuy chon; neu khong co thi van tra tat ca voucher hien co.")
                .parameter("orderValue", ToolSchema.ParameterSchema.builder()
                        .type("number")
                        .description("Tong gia tri don hang hien tai (VND), khong bat buoc.")
                        .build())
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        Double orderValue = ArgUtil.getDouble(arguments, "orderValue", null);

        try {
            Map<String, Object> activeResponse = promotionServiceClient.getActivePromotions(
                    context == null ? null : context.getAuthorizationHeader());
            List<Map<String, Object>> all = extractList(activeResponse);
            List<Map<String, Object>> eligible = orderValue == null ? all : filterByRule(all, orderValue);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderValue", orderValue);
            data.put("activePromotions", all);
            data.put("totalActive", all.size());
            data.put("eligiblePromotions", eligible);
            data.put("totalEligible", eligible.size());
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("promotionTool failed: {}", e.getMessage());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderValue", orderValue);
            data.put("activePromotions", List.of());
            data.put("totalActive", 0);
            data.put("eligiblePromotions", List.of());
            data.put("totalEligible", 0);
            data.put("warning", "Không thể lấy danh sách khuyến mãi hiện tại. Bookstore rất tiếc về sự bất tiện này.");
            return ToolResult.ok(NAME, data);
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
