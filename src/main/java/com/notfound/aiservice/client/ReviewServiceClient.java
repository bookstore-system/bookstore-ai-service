package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "review-service-client", url = "${clients.review-service.url:http://localhost:8087}")
public interface ReviewServiceClient {

    @GetMapping("/api/v1/reviews/book/{bookId}")
    Map<String, Object> getReviewsByBook(
            @PathVariable("bookId") String bookId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    );
}
