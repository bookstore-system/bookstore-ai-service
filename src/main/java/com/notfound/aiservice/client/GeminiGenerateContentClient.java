package com.notfound.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = "gemini-generate-content-client",
        url = "${clients.gemini.url:https://generativelanguage.googleapis.com}"
)
public interface GeminiGenerateContentClient {

    @PostMapping("/v1beta/models/{model}:generateContent")
    Map<String, Object> generateContent(
            @PathVariable("model") String model,
            @RequestParam("key") String apiKey,
            @RequestBody Map<String, Object> body
    );
}
