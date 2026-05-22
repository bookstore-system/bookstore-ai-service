package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "order-service-client", url = "${clients.order-service.url}")
public interface OrderServiceClient {

    @GetMapping("/api/v1/orders/admin/stats")
    Map<String, Object> getOrderStats();

    @GetMapping("/api/v1/orders/user/{userId}")
    Map<String, Object> getOrdersByUser(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    );

    @GetMapping("/api/v1/orders/{orderId}")
    Map<String, Object> getOrderById(@PathVariable("orderId") String orderId);
}
