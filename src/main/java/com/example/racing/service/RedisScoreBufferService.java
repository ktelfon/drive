package com.example.racing.service;

import com.example.racing.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisScoreBufferService {

    private final StringRedisTemplate redisTemplate;
    private final ParticipantRepository participantRepository;

    public void addScore(UUID raceId, UUID userId, int points) {
        String key = "race:" + raceId + ":scores";
        redisTemplate.opsForHash().increment(key, userId.toString(), points);
    }

    public Map<UUID, Integer> getPendingScores(UUID raceId) {
        Map<UUID, Integer> pendingScores = new HashMap<>();
        String key = "race:" + raceId + ":scores";
        String flushingKey = key + ":flushing";

        // Read from active key
        mergeScoresFromRedis(key, pendingScores);
        
        // Read from flushing key (if exists) to be accurate during flush
        mergeScoresFromRedis(flushingKey, pendingScores);

        return pendingScores;
    }

    private void mergeScoresFromRedis(String key, Map<UUID, Integer> pendingScores) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                try {
                    UUID userId = UUID.fromString((String) entry.getKey());
                    int points = Integer.parseInt((String) entry.getValue());
                    pendingScores.merge(userId, points, Integer::sum);
                } catch (Exception e) {
                    log.warn("Invalid entry in Redis score buffer for key {}: {}", key, entry, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to read pending scores from Redis for key {}", key, e);
        }
    }

    @Scheduled(fixedRate = 2000) // Flush every 2 seconds
    public void flush() {
        ScanOptions options = ScanOptions.scanOptions().match("race:*:scores").count(100).build();
        
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                flushKey(key);
            }
        }
    }

    private void flushKey(String key) {
        String tempKey = key + ":flushing";
        
        // Atomically rename to process snapshot
        Boolean renamed = redisTemplate.renameIfAbsent(key, tempKey);
        
        if (Boolean.TRUE.equals(renamed)) {
            try {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(tempKey);
                
                if (entries.isEmpty()) {
                    redisTemplate.delete(tempKey);
                    return;
                }

                // Extract raceId from key "race:{id}:scores"
                String[] parts = key.split(":");
                if (parts.length < 2) return;
                UUID raceId = UUID.fromString(parts[1]);

                for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                    UUID userId = UUID.fromString((String) entry.getKey());
                    int points = Integer.parseInt((String) entry.getValue());
                    
                    if (points != 0) {
                        try {
                            participantRepository.incrementScore(raceId, userId, points);
                        } catch (Exception e) {
                            log.error("Failed to flush score for user {} in race {}", userId, raceId, e);
                        }
                    }
                }
                
                // Cleanup processed data
                redisTemplate.delete(tempKey);
                
            } catch (Exception e) {
                log.error("Error flushing key {}", key, e);
            }
        }
    }
}
