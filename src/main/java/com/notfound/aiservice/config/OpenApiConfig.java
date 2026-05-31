package com.notfound.aiservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookstoreAiServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bookstore AI Service")
                        .description("API AI: chat, tìm kiếm, báo cáo và chatbot (microservice).")
                        .version("1.0"));
    }
}
