package com.example.racing.service;

import com.example.racing.model.RaceParticipant;
import com.example.racing.repository.ParticipantRepository;
import com.example.racing.repository.RaceRepository;
import com.example.racing.repository.RacerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RaceServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RaceService raceService;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private RacerRepository racerRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @MockitoBean
    private EngineServiceClient engineServiceClient;

    @MockitoBean
    private ReportingServiceClient reportingServiceClient;

    private UUID raceId;
    private UUID user1Id;
    private UUID user2Id;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        raceRepository.deleteAll();
        racerRepository.deleteAll();

        String raceIdStr = raceService.createRace(60);
        raceId = UUID.fromString(raceIdStr);
        user1Id = UUID.randomUUID();
        user2Id = UUID.randomUUID();

        raceService.joinRace(raceId, user1Id);
        raceService.joinRace(raceId, user2Id);
        raceService.startRace(raceId);
    }

    @Test
    void shouldFreezeOpponents_WhenAbilityOilIsUsed() {
        participantRepository.incrementScore(raceId, user1Id, 10);

        raceService.useAbilityOil(raceId, user1Id);

        List<RaceParticipant> participants = participantRepository.findAll();

        RaceParticipant user1 = participants.stream()
                .filter(p -> p.getRacer().getId().equals(user1Id))
                .findFirst().orElseThrow();

        RaceParticipant user2 = participants.stream()
                .filter(p -> p.getRacer().getId().equals(user2Id))
                .findFirst().orElseThrow();

        assertThat(user1.getFrozenUntil()).isNull();

        assertThat(user2.getFrozenUntil()).isNotNull();
        assertThat(user2.getFrozenUntil()).isAfter(Instant.now());
    }

    @Test
    void shouldDeductPointsAndPenalizeOpponents_WhenAbilityHackIsUsed() {
        participantRepository.incrementScore(raceId, user1Id, 50);
        participantRepository.incrementScore(raceId, user2Id, 50);

        raceService.useAbilityHack(raceId, user1Id);

        RaceParticipant user1 = participantRepository.findAll().stream()
                .filter(p -> p.getRacer().getId().equals(user1Id)).findFirst().orElseThrow();
        RaceParticipant user2 = participantRepository.findAll().stream()
                .filter(p -> p.getRacer().getId().equals(user2Id)).findFirst().orElseThrow();

        assertThat(user1.getScore()).isEqualTo(30);

        assertThat(user2.getScore()).isBetween(39, 50);
    }
}
