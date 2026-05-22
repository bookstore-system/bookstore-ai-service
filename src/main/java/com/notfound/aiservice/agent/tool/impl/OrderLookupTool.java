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
 * Tra cứu đơn hàng cho user. Hỗ trợ 2 chế độ:
 *  - byOrderId: nếu user/AI biết mã đơn
 *  - byUserId: lấy danh sách đơn của user hiện tại
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
                .description("Tra cứu đơn hàng của user. Dùng khi user hỏi 'đơn của tôi đến đâu', 'trạng thái đơn x'.")
                .parameter("orderId", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("UUID của đơn hàng cụ thể.")
                        .build())
                .parameter("userId", ToolSchema.ParameterSchema.builder()
                        .type("string")
                        .description("UUID của user (nếu cần liệt kê tất cả đơn).")
                        .build())
                .parameter("size", ToolSchema.ParameterSchema.builder()
                        .type("integer")
                        .description("Số đơn cần lấy (mặc định 5).")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        try {
            String orderId = ArgUtil.getString(arguments, "orderId", "");
            String userId = ArgUtil.getString(arguments, "userId", context == null ? "" : context.getUserId());
            int size = ArgUtil.getInt(arguments, "size", 5);

            Map<String, Object> data = new LinkedHashMap<>();
            if (!orderId.isBlank()) {
                Map<String, Object> order = safeCall(() -> orderServiceClient.getOrderById(orderId.trim()));
                data.put("mode", "byOrderId");
                data.put("orderId", orderId);
                data.put("order", order);
                return ToolResult.ok(NAME, data);
            }

            if (userId != null && !userId.isBlank()) {
                Map<String, Object> orders = safeCall(() -> orderServiceClient.getOrdersByUser(userId.trim(), 0, size));
                data.put("mode", "byUserId");
                data.put("userId", userId);
                data.put("orders", orders);
                return ToolResult.ok(NAME, data);
            }

            return ToolResult.fail(NAME, "Cần cung cấp orderId hoặc userId.");
        } catch (Exception e) {
            log.warn("orderLookupTool failed", e);
            return ToolResult.fail(NAME, "Không tra cứu được đơn hàng: " + e.getMessage());
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
