package com.example.racing.service;

import com.example.racing.model.Race;
import com.example.racing.model.RaceStatus;
import com.example.racing.repository.RaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RaceRecoveryTask {
    private final RaceRepository raceRepository;
    private final RaceSchedulerService raceSchedulerService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverActiveRaces() {
        log.info("Checking for races that should have finished while server was down...");

        List<Race> activeRaces = raceRepository.findAllByState(RaceStatus.ACTIVE);

        for (Race race : activeRaces) {
            LocalDateTime finishTime = race.getStartedAt().plusSeconds(race.getDurationInSeconds());

            if (LocalDateTime.now().isAfter(finishTime)) {
                raceSchedulerService.finishRace(race.getId());
            } else {
                java.time.Instant finishInstant = finishTime.toInstant(java.time.ZoneOffset.UTC);
                raceSchedulerService.scheduleRaceFinish(race.getId(), finishInstant);
            }
        }
    }
}
