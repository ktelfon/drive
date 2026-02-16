package com.example.racing.repository;

import com.example.racing.dto.ParticipantFrozenStatus;
import com.example.racing.model.RaceParticipant;
import com.example.racing.model.RaceStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<RaceParticipant , UUID> {

    @Query("SELECT COUNT(p) > 0 FROM RaceParticipant p WHERE p.racer.id = :racerId AND p.race.state IN (:statuses)")
    boolean existsByRacerIdAndRaceStateIn(UUID racerId, RaceStatus... statuses);

    @Query("SELECT new com.example.racing.dto.ParticipantFrozenStatus(p.frozenUntil) FROM RaceParticipant p WHERE p.race.id = :raceId AND p.racer.id = :racerId")
    Optional<ParticipantFrozenStatus> findFrozenStatus(UUID raceId, UUID racerId);

    @Modifying
    @Transactional
    @Query("UPDATE RaceParticipant p SET p.score = p.score + :points " +
            "WHERE p.race.id = :raceId AND p.racer.id = :racerId")
    void incrementScore(UUID raceId, UUID racerId, int points);

    @Modifying
    @Transactional
    @Query("UPDATE RaceParticipant p SET p.score = p.score - :cost " +
            "WHERE p.race.id = :raceId AND p.racer.id = :racerId AND p.score >= :cost")
    int deductScoreIfSufficient(UUID raceId, UUID racerId, int cost);

    @Modifying
    @Transactional
    @Query(value = "UPDATE race_participant SET score = GREATEST(0, score - CAST(FLOOR(RANDOM() * :limit) AS INTEGER)) " +
            "WHERE race_id = :raceId AND racer_id != :excludedRacerId", nativeQuery = true)
    void applyRandomPenalty(UUID raceId, UUID excludedRacerId, int limit);

    @Modifying
    @Transactional
    @Query(value = "UPDATE race_participant " +
            "SET frozen_until = (CASE WHEN frozen_until > NOW() THEN frozen_until ELSE NOW() END) + make_interval(secs => :durationSeconds) " +
            "WHERE race_id = :raceId AND racer_id != :excludedRacerId", nativeQuery = true)
    void applyOilSlick(UUID raceId, UUID excludedRacerId, double durationSeconds);
}
