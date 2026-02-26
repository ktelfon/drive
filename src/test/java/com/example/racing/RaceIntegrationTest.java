package com.example.racing;

import com.example.racing.dto.CreateRaceRequest;
import com.example.racing.service.EngineServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RaceIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("mydatabase")
            .withUsername("myuser")
            .withPassword("secret");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer wireMockServer = new WireMockServer(0); // Dynamic port

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();
        System.setProperty("racing.reporting.url", wireMockServer.baseUrl() + "/api/fragile-fast/reporting/winners");
        System.setProperty("racing.reporting.fallback-url", wireMockServer.baseUrl() + "/api/stable-slow/reporting/winners");
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
        System.clearProperty("racing.reporting.url");
        System.clearProperty("racing.reporting.fallback-url");
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        
        registry.add("racing.reporting.url", () -> wireMockServer.baseUrl() + "/api/fragile-fast/reporting/winners");
        registry.add("racing.reporting.fallback-url", () -> wireMockServer.baseUrl() + "/api/stable-slow/reporting/winners");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment env;

    @MockitoBean
    private EngineServiceClient engineServiceClient;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
    }

    @Test
    void fullRaceLifeCycleTest() throws Exception {
        String reportingUrl = env.getProperty("racing.reporting.url");
        assertThat(reportingUrl).contains("localhost:" + wireMockServer.port());

        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.post(urlPathMatching("/api/.*"))
                .willReturn(aResponse().withStatus(200)));

        int raceDurationSeconds = 2;
        CreateRaceRequest createReq = new CreateRaceRequest(raceDurationSeconds);
        MvcResult createResult = mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String raceIdStr = objectMapper.readTree(responseBody).get("raceId").asText();
        UUID raceId = UUID.fromString(raceIdStr);

        mockMvc.perform(post("/" + raceId + "/join")
                        .header("X-User-ID", user1Id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/" + raceId + "/join")
                        .header("X-User-ID", user2Id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/" + raceId + "/start"))
                .andExpect(status().isOk());

        when(engineServiceClient.fetchPoints()).thenReturn(10, 5, 8, 3);

        mockMvc.perform(post("/" + raceId + "/drive")
                        .header("X-User-ID", user1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(10));

        mockMvc.perform(post("/" + raceId + "/drive")
                        .header("X-User-ID", user2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(5));

        mockMvc.perform(post("/" + raceId + "/drive")
                        .header("X-User-ID", user1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(8));

        mockMvc.perform(post("/" + raceId + "/drive")
                        .header("X-User-ID", user2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(3));

        // Wait for Redis flush (every 2 seconds)
        await().atMost(4, TimeUnit.SECONDS).untilAsserted(() -> {
            mockMvc.perform(get("/" + raceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.leaderboard[0].score").value(18))
                    .andExpect(jsonPath("$.leaderboard[1].score").value(8));
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            mockMvc.perform(get("/" + raceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FINISHED"));
        });

        mockMvc.perform(get("/" + raceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.leaderboard[0].rank").value(1))
                .andExpect(jsonPath("$.leaderboard[0].racerId").value(user1Id.toString()))
                .andExpect(jsonPath("$.leaderboard[0].score").value(18))
                .andExpect(jsonPath("$.leaderboard[1].rank").value(2))
                .andExpect(jsonPath("$.leaderboard[1].racerId").value(user2Id.toString()))
                .andExpect(jsonPath("$.leaderboard[1].score").value(8));

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            wireMockServer.verify(postRequestedFor(urlPathMatching("/api/.*")));
        });
    }

    @Test
    void abilitiesTest() throws Exception {
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        int raceDurationSeconds = 60;
        CreateRaceRequest createReq = new CreateRaceRequest(raceDurationSeconds);
        MvcResult createResult = mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String raceIdStr = objectMapper.readTree(responseBody).get("raceId").asText();
        UUID raceId = UUID.fromString(raceIdStr);

        mockMvc.perform(post("/" + raceId + "/join")
                        .header("X-User-ID", user1Id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/" + raceId + "/join")
                        .header("X-User-ID", user2Id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/" + raceId + "/start"))
                .andExpect(status().isOk());

        when(engineServiceClient.fetchPoints()).thenReturn(50);
        mockMvc.perform(post("/" + raceId + "/drive")
                        .header("X-User-ID", user1Id))
                .andExpect(status().isOk());
        mockMvc.perform(post("/" + raceId + "/drive")
                        .header("X-User-ID", user2Id))
                .andExpect(status().isOk());

        // Wait for flush before using abilities (since abilities check DB score)
        await().atMost(4, TimeUnit.SECONDS).untilAsserted(() -> {
             mockMvc.perform(get("/" + raceId))
                    .andExpect(jsonPath("$.leaderboard[0].score").value(50));
        });

        mockMvc.perform(post("/" + raceId + "/abilities/oil-slick")
                        .header("X-User-ID", user1Id))
                .andExpect(status().isOk());

        mockMvc.perform(get("/" + raceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboard[?(@.racerId == '" + user1Id + "')].score").value(40));

        mockMvc.perform(post("/" + raceId + "/abilities/engine-hack")
                        .header("X-User-ID", user2Id))
                .andExpect(status().isOk());

        mockMvc.perform(get("/" + raceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboard[?(@.racerId == '" + user2Id + "')].score").value(30));
    }
}