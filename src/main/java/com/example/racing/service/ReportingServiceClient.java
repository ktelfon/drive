package com.example.racing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ReportingServiceClient {
    private final RestClient restClient;
    private final String primaryUrl;
    private final String fallbackUrl;

    public ReportingServiceClient(RestClient.Builder builder,
                                  @Value("${racing.reporting.url}") String primaryUrl,
                                  @Value("${racing.reporting.fallback-url}") String fallbackUrl,
                                  @Value("${racing.reporting.connect-timeout-seconds:3}") int connectTimeoutSeconds,
                                  @Value("${racing.reporting.read-timeout-seconds:10}") int readTimeoutSeconds) {
        this.restClient = builder
                .setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();
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

    private void callApi(String url, ReportWinnersRequest request) {
        restClient.post()
                .uri(url)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public record ReportWinnersRequest(UUID raceId, List<WinnerDto> winners) {}

    public record WinnerDto(int rank, UUID userId, int score) {}
}
