package com.example.racing.dto;

import com.example.racing.model.RaceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RaceDetailsResponse {
    private UUID raceId;
    private int durationInSeconds;
    private RaceStatus status;
    private LocalDateTime startedAt;
    private List<LeaderboardEntry> leaderboard;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class LeaderboardEntry {
        private int rank;
        private UUID racerId;
        private int score;
    }
}
