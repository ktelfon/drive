package com.example.racing.controller;

import com.example.racing.dto.CreateRaceRequest;
import com.example.racing.dto.DriveResponse;
import com.example.racing.dto.RaceDetailsResponse;
import com.example.racing.dto.RaceResponse;
import com.example.racing.service.RaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RaceController {

    private final RaceService raceService;

    @PostMapping
    public ResponseEntity<RaceResponse> createRace(@RequestBody @Valid CreateRaceRequest request) {
        String raceId = raceService.createRace(request.durationInSeconds());
        return ResponseEntity.ok(new RaceResponse(raceId));
    }

    @PostMapping("/{raceId}/join")
    public ResponseEntity<Void> joinRace(@PathVariable UUID raceId, @RequestHeader("X-User-ID") UUID userId) {
        raceService.joinRace(raceId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{raceId}/start")
    public ResponseEntity<Void> startRace(@PathVariable UUID raceId) {
        raceService.startRace(raceId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{raceId}/drive")
    public ResponseEntity<DriveResponse> drive(@PathVariable UUID raceId, @RequestHeader("X-User-ID") UUID userId) {
        int points = raceService.drive(raceId, userId);
        return ResponseEntity.ok(new DriveResponse(points));
    }

    @PostMapping("/{raceId}/abilities/oil-slick")
    public ResponseEntity<Void> oilSlick(@PathVariable UUID raceId, @RequestHeader("X-User-ID") UUID userId) {
        raceService.useAbilityOil(raceId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{raceId}/abilities/engine-hack")
    public ResponseEntity<Void> engineHack(@PathVariable UUID raceId, @RequestHeader("X-User-ID") UUID userId) {
        raceService.useAbilityHack(raceId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{raceId}")
    public ResponseEntity<RaceDetailsResponse> getRace(
            @PathVariable UUID raceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(raceService.getRaceDetails(raceId, page, size));
    }
}
