package com.notfound.aiservice.agent.tool.impl;

import com.notfound.aiservice.agent.tool.Tool;
import com.notfound.aiservice.agent.tool.ToolContext;
import com.notfound.aiservice.agent.tool.ToolResult;
import com.notfound.aiservice.agent.tool.ToolSchema;
import com.notfound.aiservice.client.OrderServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Look up orders for the current authenticated user only.
 * Do not accept user-provided order IDs here: a guessed/copied ID must not let
 * the chatbot disclose another user's order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLookupTool implements Tool {

    public static final String NAME = "orderLookupTool";

    private final OrderServiceClient orderServiceClient;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description("Tra cuu danh sach don hang cua user dang dang nhap. Dung khi user hoi don cua toi, trang thai don hang, lich su mua hang. Khong yeu cau va khong dung orderId do user nhap.")
                .parameter("size", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("So don gan nhat can hien thi, mac dinh 5.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            String userId = context == null ? "" : context.getUserId();
            int size = ArgUtil.getInt(arguments, "size", 5);
            if (userId == null || userId.isBlank()) {
                return ToolResult.fail(NAME, "Ban can dang nhap de xem don hang cua minh.");
            }

            Map<String, Object> orders = safeCall(() -> orderServiceClient.getMyOrders(userId.trim()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("mode", "currentUserOrders");
            data.put("userId", userId);
            data.put("size", size);
            data.put("orders", orders);
            log.info(
                    "orderLookupTool fetched orders for current user: userId={}, responseKeys={}",
                    userId,
                    orders == null ? null : orders.keySet()
            );
            return ToolResult.ok(NAME, data);
        } catch (Exception e) {
            log.warn("orderLookupTool failed", e);
            return ToolResult.fail(NAME, "Khong tra cuu duoc don hang: " + e.getMessage());
        }
    }

    private Map<String, Object> safeCall(SupplierWithException<Map<String, Object>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
