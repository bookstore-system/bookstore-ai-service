package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "order-service-client", url = "${clients.order-service.url}")
public interface OrderServiceClient {

    @GetMapping("/api/v1/orders/admin/stats")
    Map<String, Object> getOrderStats();

    @GetMapping("/api/v1/orders")
    Map<String, Object> getMyOrders(@RequestHeader("X-User-Id") String userId);

}
