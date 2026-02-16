package com.example.racing.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EngineServiceClient {
    private final RestClient restClient;
    private final String primaryUrl;
    private final String fallbackUrl;

    public EngineServiceClient(RestClient.Builder builder,
                               @Value("${racing.engine.url}") String primaryUrl,
                               @Value("${racing.engine.fallback-url}") String fallbackUrl) {
        this.restClient = builder.build();
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
        PointsResponse response = restClient.post()
                .uri(url)
                .retrieve()
                .body(PointsResponse.class);
        return (response != null) ? response.getPoints() : 0;
    }

    @Data
    static class PointsResponse {
        private int points;
    }
}
