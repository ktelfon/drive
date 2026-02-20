package com.example.racing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ReportingServiceClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String primaryUrl;
    private final String fallbackUrl;

    public ReportingServiceClient(RestTemplateBuilder builder,
                                  ObjectMapper objectMapper,
                                  @Value("${racing.reporting.url}") String primaryUrl,
                                  @Value("${racing.reporting.fallback-url}") String fallbackUrl,
                                  @Value("${racing.reporting.connect-timeout-seconds:3}") int connectTimeoutSeconds,
                                  @Value("${racing.reporting.read-timeout-seconds:10}") int readTimeoutSeconds) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();
        this.objectMapper = objectMapper;
        this.primaryUrl = primaryUrl;
        this.fallbackUrl = fallbackUrl;
    }

    public void reportWinners(UUID raceId, List<WinnerDto> winners) {
        ReportWinnersRequest request = new ReportWinnersRequest(raceId, winners);
        try {
            callApi(primaryUrl, request);
        } catch (Exception e) {
            log.warn("Primary reporting service failed, trying fallback...", e);
            try {
                callApi(fallbackUrl, request);
            } catch (Exception ex) {
                log.error("Both reporting services failed to report winners for race {}", raceId, ex);
            }
        }
    }

    private void callApi(String url, ReportWinnersRequest request) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(request);
        log.info("Sending report to {}: {}", url, jsonBody);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        
        restTemplate.postForEntity(url, entity, Void.class);
    }

    public record ReportWinnersRequest(UUID raceId, List<WinnerDto> winners) {}

    public record WinnerDto(int rank, UUID userId, int score) {}
}
