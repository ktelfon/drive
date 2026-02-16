package com.example.racing.repository;

import com.example.racing.model.Race;
import com.example.racing.model.RaceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceRepository extends JpaRepository<Race, UUID> {
    List<Race> findAllByState(RaceStatus raceStatus);

    @Query("SELECT r.state FROM Race r WHERE r.id = :raceId")
    Optional<RaceStatus> findStateById(UUID raceId);
}
