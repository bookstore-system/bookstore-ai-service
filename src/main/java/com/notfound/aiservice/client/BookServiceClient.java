package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/api/v1/books/filter")
    Map<String, Object> filterBooks(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @RequestParam(value = "minRating", required = false) Double minRating,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    );

    @GetMapping("/api/v1/books/best-selling")
    Map<String, Object> getBestSelling(@RequestParam(value = "limit", required = false) Integer limit);

    @GetMapping("/api/v1/books/suggested")
    Map<String, Object> getSuggested(@RequestParam(value = "limit", required = false) Integer limit);

    @GetMapping("/api/v1/books/{id}")
    Map<String, Object> getBookById(@PathVariable("id") String id);

    @PostMapping("/api/v1/books/batch-details")
    Map<String, Object> getBatchBookDetails(@RequestBody Map<String, Object> body);

    @GetMapping("/api/v1/categories")
    Map<String, Object> getCategories();
}
