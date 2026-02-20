package com.example.racing.service;

import com.example.racing.model.Race;
import com.example.racing.model.RaceParticipant;
import com.example.racing.model.RaceStatus;
import com.example.racing.repository.RaceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaceSchedulerService {

    private final RaceRepository raceRepository;
    private final TaskScheduler taskScheduler;
    private final ReportingServiceClient reportingServiceClient;

    public void scheduleRaceFinish(UUID raceId, Instant finishTime) {
        taskScheduler.schedule(() -> finishRace(raceId), finishTime);
        log.info("Scheduled race {} to finish at {}", raceId, finishTime);
    }

    @Transactional
    public void finishRace(UUID raceId) {
        int updated = raceRepository.transitionState(raceId, RaceStatus.ACTIVE, RaceStatus.FINISHED);

        if (updated == 0) {
            log.debug("Race {} was already finished or not found, skipping.", raceId);
            return;
        }

        log.info("Race {} finished.", raceId);

        Race race = raceRepository.findById(raceId).orElseThrow();
        List<RaceParticipant> participants = race.getParticipants();

        if (participants.isEmpty()) {
            log.warn("No participants found for race {} when finishing!", raceId);
        }

        List<ReportingServiceClient.WinnerDto> top3 = IntStream.range(0, Math.min(participants.size(), 3))
                .mapToObj(i -> {
                    RaceParticipant p = participants.get(i);
                    return new ReportingServiceClient.WinnerDto(i + 1, p.getRacer().getId(), p.getScore());
                })
                .collect(Collectors.toList());

        if (!top3.isEmpty()) {
            reportingServiceClient.reportWinners(raceId, top3);
        } else {
            log.warn("Top 3 list is empty for race {}, skipping reporting.", raceId);
        }
    }
}
