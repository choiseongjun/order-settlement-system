package com.example.order.infrastructure.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 멱등성 보장 서비스 (Redis 기반)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final long TTL_DAYS = 7;

    public boolean isProcessed(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    public void markAsProcessed(String key) {
        redisTemplate.opsForValue().set(
            key,
            LocalDateTime.now().toString(),
            Duration.ofDays(TTL_DAYS)
        );
        log.debug("Marked as processed: {}", key);
    }

    public void remove(String key) {
        redisTemplate.delete(key);
        log.debug("Removed idempotency key: {}", key);
    }
}
