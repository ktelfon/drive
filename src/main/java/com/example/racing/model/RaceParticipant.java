package com.example.racing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(indexes = {
        @Index(name = "idx_race_participant_race_id", columnList = "race_id"),
        @Index(name = "idx_race_participant_racer_id", columnList = "racer_id")
})
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
