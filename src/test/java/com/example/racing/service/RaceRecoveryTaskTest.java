package com.example.racing.service;

import com.example.racing.model.Race;
import com.example.racing.model.RaceStatus;
import com.example.racing.repository.RaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaceRecoveryTaskTest {

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private RaceSchedulerService raceSchedulerService;

    @InjectMocks
    private RaceRecoveryTask raceRecoveryTask;

    @BeforeEach
    void setUp() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    @Test
    void recoverActiveRaces_ShouldFinishOverdueRaces() {
        UUID raceId = UUID.randomUUID();
        Race overdueRace = new Race();
        overdueRace.setId(raceId);
        overdueRace.setState(RaceStatus.ACTIVE);
        overdueRace.setStartedAt(LocalDateTime.now().minusMinutes(10));
        overdueRace.setDurationInSeconds(60);

        when(raceRepository.findAllByState(RaceStatus.ACTIVE)).thenReturn(List.of(overdueRace));

        raceRecoveryTask.recoverActiveRaces();

        verify(raceSchedulerService).finishRace(raceId);
        verify(raceSchedulerService, never()).scheduleRaceFinish(any(), any());
    }

    @Test
    void recoverActiveRaces_ShouldRescheduleActiveRaces() {
        UUID raceId = UUID.randomUUID();
        Race activeRace = new Race();
        activeRace.setId(raceId);
        activeRace.setState(RaceStatus.ACTIVE);
        activeRace.setStartedAt(LocalDateTime.now());
        activeRace.setDurationInSeconds(300);

        when(raceRepository.findAllByState(RaceStatus.ACTIVE)).thenReturn(List.of(activeRace));

        raceRecoveryTask.recoverActiveRaces();

        verify(raceSchedulerService, never()).finishRace(any());
        verify(raceSchedulerService).scheduleRaceFinish(eq(raceId), any(Instant.class));
    }

    @Test
    void recoverActiveRaces_ShouldHandleMixedRaces() {
        UUID overdueRaceId = UUID.randomUUID();
        Race overdueRace = new Race();
        overdueRace.setId(overdueRaceId);
        overdueRace.setState(RaceStatus.ACTIVE);
        overdueRace.setStartedAt(LocalDateTime.now().minusMinutes(10));
        overdueRace.setDurationInSeconds(60);

        UUID activeRaceId = UUID.randomUUID();
        Race activeRace = new Race();
        activeRace.setId(activeRaceId);
        activeRace.setState(RaceStatus.ACTIVE);
        activeRace.setStartedAt(LocalDateTime.now());
        activeRace.setDurationInSeconds(300);

        when(raceRepository.findAllByState(RaceStatus.ACTIVE)).thenReturn(List.of(overdueRace, activeRace));

        raceRecoveryTask.recoverActiveRaces();

        verify(raceSchedulerService).finishRace(overdueRaceId);
        verify(raceSchedulerService).scheduleRaceFinish(eq(activeRaceId), any(Instant.class));
    }

    @Test
    void recoverActiveRaces_ShouldDoNothingIfNoActiveRaces() {
        when(raceRepository.findAllByState(RaceStatus.ACTIVE)).thenReturn(Collections.emptyList());

        raceRecoveryTask.recoverActiveRaces();

        verify(raceSchedulerService, never()).finishRace(any());
        verify(raceSchedulerService, never()).scheduleRaceFinish(any(), any());
    }
}
