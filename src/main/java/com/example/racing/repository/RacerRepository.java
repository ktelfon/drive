package com.example.racing.repository;

import com.example.racing.model.Racer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RacerRepository extends JpaRepository<Racer, UUID> {
}
