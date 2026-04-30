package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "book-service-client", url = "${clients.book-service.url}")
public interface BookServiceClient {

    @GetMapping("/api/v1/books")
    Map<String, Object> searchBooks(
            @RequestParam("page") Integer page,
            @RequestParam("size") Integer size,
            @RequestParam("keyword") String keyword
    );
}
