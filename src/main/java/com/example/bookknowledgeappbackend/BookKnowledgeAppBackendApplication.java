package com.example.bookknowledgeappbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableScheduling
public class BookKnowledgeAppBackendApplication {
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
    public static void main(String[] args) {
        SpringApplication.run(BookKnowledgeAppBackendApplication.class, args);
    }

}
