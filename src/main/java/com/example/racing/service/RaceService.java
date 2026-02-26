package com.example.racing.service;

import com.example.racing.dto.ParticipantFrozenStatus;
import com.example.racing.dto.RaceDetailsResponse;
import com.example.racing.model.Race;
import com.example.racing.model.RaceParticipant;
import com.example.racing.model.RaceStatus;
import com.example.racing.model.Racer;
import com.example.racing.repository.ParticipantRepository;
import com.example.racing.repository.RaceRepository;
import com.example.racing.repository.RacerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
@Slf4j
public class RaceService {

    private final RaceRepository raceRepository;
    private final RacerRepository racerRepository;
    private final ParticipantRepository participantRepository;
    private final EngineServiceClient engineServiceClient;
    private final RaceSchedulerService raceSchedulerService;
    private final RedisScoreBufferService redisScoreBufferService;

    @Value("${racing.game.oil-slick-cost:10}")
    private int oilSlickCost;

    @Value("${racing.game.engine-hack-cost:20}")
    private int engineHackCost;

    @Value("${racing.game.freeze-percentage:0.01}")
    private double freezePercentage;

    @Value("${racing.game.hack-penalty-limit:11}")
    private int hackPenaltyLimit;

    public String createRace(int durationInSeconds) {
        Race race = new Race();
        race.setDurationInSeconds(durationInSeconds);
        race = raceRepository.save(race);
        return race.getId().toString();
    }

    @Transactional
    public void joinRace(UUID raceId, UUID userId) {
        Race race = raceRepository.findById(raceId).orElseThrow();
        if (race.getState() != RaceStatus.CREATED) {
            throw new IllegalStateException("Cannot join a race.");
        }

        boolean isAlreadyRacing = participantRepository.existsByRacerIdAndRaceStateIn(userId, RaceStatus.CREATED, RaceStatus.ACTIVE);
        if (isAlreadyRacing) {
            throw new IllegalStateException("Racer is already participating in another active or pending race.");
        }

        Racer newRacer = new Racer();
        newRacer.setId(userId);
        Racer racer = racerRepository.findById(userId)
                .orElseGet(() -> racerRepository.save(newRacer));

        RaceParticipant participation = new RaceParticipant();
        participation.setRace(race);
        participation.setRacer(racer);
        participation.setScore(0);

        participantRepository.save(participation);
    }

    @Transactional
    public void startRace(UUID raceId) {
        Race race = raceRepository.findById(raceId).orElseThrow();
        if (race.getState() != RaceStatus.CREATED) {
            throw new IllegalStateException("Cannot start a race.");
        }
        race.setStartedAt(LocalDateTime.now());
        race.setState(RaceStatus.ACTIVE);
        raceRepository.save(race);

        Instant finishTime = Instant.now().plusSeconds(race.getDurationInSeconds());

        raceSchedulerService.scheduleRaceFinish(raceId, finishTime);

        log.debug("Race {} started. Will automatically finish at {}", raceId, finishTime);
    }

    @Transactional
    public RaceDetailsResponse getRaceDetails(UUID raceId, int page, int size) {
        Race race = raceRepository.findById(raceId).orElseThrow();

        if (race.getState() == RaceStatus.ACTIVE
                && LocalDateTime.now().isAfter(race.getStartedAt().plusSeconds(race.getDurationInSeconds()))) {

            raceSchedulerService.finishRace(raceId);

            race = raceRepository.findById(raceId).orElseThrow();
        }

        // Fetch pending scores from Redis
        Map<UUID, Integer> pendingScores = redisScoreBufferService.getPendingScores(raceId);

        List<RaceParticipant> allParticipants = race.getParticipants();
        
        // Merge DB scores with pending scores
        allParticipants.forEach(p -> {
            Integer pending = pendingScores.get(p.getRacer().getId());
            if (pending != null) {
                p.setScore(p.getScore() + pending);
            }
        });

        // Re-sort because scores changed
        allParticipants.sort(Comparator.comparingInt(RaceParticipant::getScore).reversed());

        int total = allParticipants.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<RaceParticipant> pageParticipants = allParticipants.subList(fromIndex, toIndex);

        List<RaceDetailsResponse.LeaderboardEntry> leaderboard = IntStream.range(0, pageParticipants.size())
                .mapToObj(i -> {
                    RaceParticipant p = pageParticipants.get(i);
                    return RaceDetailsResponse.LeaderboardEntry.builder()
                            .rank(fromIndex + i + 1)
                            .racerId(p.getRacer().getId())
                            .score(p.getScore())
                            .build();
                })
                .collect(Collectors.toList());

        return RaceDetailsResponse.builder()
                .raceId(race.getId())
                .durationInSeconds(race.getDurationInSeconds())
                .status(race.getState())
                .startedAt(race.getStartedAt())
                .leaderboard(leaderboard)
                .createdAt(race.getCreatedAt())
                .updatedAt(race.getUpdatedAt())
                .totalParticipants(total)
                .page(page)
                .size(size)
                .build();
    }

    public int drive(UUID raceId, UUID userId) {
        RaceStatus raceStatus = raceRepository.findStateById(raceId)
                .orElseThrow(() -> new RuntimeException("Race not found"));

        if (raceStatus != RaceStatus.ACTIVE) {
            throw new IllegalStateException("Race is not active");
        }

        ParticipantFrozenStatus frozenStatus = participantRepository.findFrozenStatus(raceId, userId)
                .orElseThrow(() -> new IllegalStateException("Participant not found in this race"));

        Instant frozenUntil = frozenStatus.frozenUntil();
        if (frozenUntil != null && frozenUntil.isAfter(Instant.now())) {
            log.debug("Racer is frozen, skipping drive {}", userId);
            return 0;
        }

        int earnedPoints = engineServiceClient.fetchPoints();

        if (earnedPoints > 0) {
            redisScoreBufferService.addScore(raceId, userId, earnedPoints);
        }

        return earnedPoints;
    }

    @Transactional
    public void useAbilityOil(UUID raceId, UUID userId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new RuntimeException("Race not found"));

        if (race.getState() != RaceStatus.ACTIVE) {
            throw new IllegalStateException("Race is not active");
        }

        int updatedRows = participantRepository.deductScoreIfSufficient(raceId, userId, oilSlickCost);
        if (updatedRows == 0) {
            throw new IllegalStateException("Insufficient points or participant not found");
        }

        double freezeDurationSeconds = race.getDurationInSeconds() * freezePercentage;
        participantRepository.applyOilSlick(raceId, userId, freezeDurationSeconds);
    }

    @Transactional
    public void useAbilityHack(UUID raceId, UUID userId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new RuntimeException("Race not found"));

        if (race.getState() != RaceStatus.ACTIVE) {
            throw new IllegalStateException("Race is not active");
        }

        int updatedRows = participantRepository.deductScoreIfSufficient(raceId, userId, engineHackCost);
        if (updatedRows == 0) {
            throw new IllegalStateException("Insufficient points or participant not found");
        }

        participantRepository.applyRandomPenalty(raceId, userId, hackPenaltyLimit);
    }
}
