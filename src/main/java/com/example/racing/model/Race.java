package com.example.racing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Race {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime startedAt;
    private int durationInSeconds;
    private int timeLeft;
    private RaceStatus state = RaceStatus.CREATED;

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL)
    @OrderBy("score DESC")
    private List<RaceParticipant> participants = new ArrayList<>();

    public RaceStatus getStatus() {
        if (state == RaceStatus.ACTIVE && startedAt != null) {
            if (LocalDateTime.now().isAfter(startedAt.plusSeconds(durationInSeconds))) {
                return RaceStatus.FINISHED;
            }
        }
        return state;
    }
}
