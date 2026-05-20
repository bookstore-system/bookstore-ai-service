package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@FeignClient(name = "promotion-service-client", url = "${clients.promotion-service.url:http://localhost:8086}")
public interface PromotionServiceClient {

    @GetMapping("/api/promotions/active")
    Map<String, Object> getActivePromotions();

    @GetMapping("/api/promotions")
    Map<String, Object> getAllPromotions();
}
