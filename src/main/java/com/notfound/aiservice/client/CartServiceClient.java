package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "cart-service-client", url = "${clients.cart-service.url}")
public interface CartServiceClient {

    @PostMapping("/api/v1/cart/add")
    Map<String, Object> addToCart(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body
    );
}
