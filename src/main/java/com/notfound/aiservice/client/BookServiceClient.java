package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "book-service-client", url = "${clients.book-service.url}")
public interface BookServiceClient {

    @GetMapping("/api/v1/books/search")
    Map<String, Object> searchBooks(
            @RequestParam("keyword") String keyword,
            @RequestParam("page") Integer page,
            @RequestParam("size") Integer size
    );
}
