package com.example.racing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
public class RaceParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "race_id")
    @JsonIgnore
    private Race race;

    @ManyToOne
    @JoinColumn(name = "racer_id")
    private Racer racer;

    private int score = 0;
    private Instant frozenUntil;
}
