package com.example.racing.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Service
public class EngineServiceClient {
    private final RestTemplate restTemplate;
    private final String primaryUrl;
    private final String fallbackUrl;

    public EngineServiceClient(RestTemplateBuilder builder,
                               @Value("${racing.engine.url}") String primaryUrl,
                               @Value("${racing.engine.fallback-url}") String fallbackUrl,
                               @Value("${racing.engine.connect-timeout-seconds:3}") int connectTimeoutSeconds,
                               @Value("${racing.engine.read-timeout-seconds:5}") int readTimeoutSeconds) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();
        this.primaryUrl = primaryUrl;
        this.fallbackUrl = fallbackUrl;
    }

    public int fetchPoints() {
        try {
            return callApi(primaryUrl);
        } catch (Exception e) {
            try {
                return callApi(fallbackUrl);
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    private int callApi(String url) {
        PointsResponse response = restTemplate.postForObject(url, null, PointsResponse.class);
        return (response != null) ? response.getPoints() : 0;
    }

    @Data
    static class PointsResponse {
        private int points;
    }
}
