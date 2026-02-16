package com.example.racing.dto;

import jakarta.validation.constraints.Min;

public record CreateRaceRequest(
        @Min(value = 1, message = "Duration must be at least 1 second")
        int durationInSeconds
) {
}
