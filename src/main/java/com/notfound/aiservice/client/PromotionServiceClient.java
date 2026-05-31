package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "promotion-service-client", url = "${clients.promotion-service.url:http://localhost:8086}")
public interface PromotionServiceClient {

    @GetMapping("/api/v1/promotions/active")
    Map<String, Object> getActivePromotions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader);

    @GetMapping("/api/v1/promotions")
    Map<String, Object> getAllPromotions();
}
