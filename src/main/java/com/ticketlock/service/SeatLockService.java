package com.ticketlock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Distributed seat locking using Redis.
 *
 * Lock key format: seat:lock:{seatId}
 * Value: unique lock token (for safe release)
 * TTL: configured hold duration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeatLockService {

    private final StringRedisTemplate redisTemplate;

    @Value("${ticketlock.seat-hold-duration-minutes:8}")
    private int holdDurationMinutes;

    private static final String LOCK_PREFIX = "seat:lock:";

    /**
     * Try to acquire locks for multiple seats.
     * Uses SET NX EX for atomic lock acquisition.
     *
     * @return lockToken if ALL seats were locked, null if any seat failed
     */
    public String tryLockSeats(List<Long> seatIds) {
        String lockToken = UUID.randomUUID().toString();
        Duration ttl = Duration.ofMinutes(holdDurationMinutes);

        List<Long> lockedSeats = new java.util.ArrayList<>();

        try {
            for (Long seatId : seatIds) {
                String key = LOCK_PREFIX + seatId;
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(key, lockToken, ttl);

                if (Boolean.FALSE.equals(acquired) || acquired == null) {
                    log.warn("Failed to acquire lock for seatId={}", seatId);
                    // Rollback already acquired locks
                    releaseLocks(lockedSeats, lockToken);
                    return null;
                }
                lockedSeats.add(seatId);
            }

            log.info("Acquired locks for seats={} with token={}", seatIds, lockToken);
            return lockToken;

        } catch (Exception e) {
            log.error("Error while acquiring seat locks", e);
            releaseLocks(lockedSeats, lockToken);
            return null;
        }
    }

    /**
     * Release locks only if we still own them (compare token).
     * Uses Lua script for atomic check-and-delete.
     */
    public void releaseLocks(List<Long> seatIds, String lockToken) {
        if (seatIds == null || seatIds.isEmpty() || lockToken == null) {
            return;
        }

        String luaScript =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "  return redis.call('del', KEYS[1]) " +
                "else " +
                "  return 0 " +
                "end";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(Long.class);

        for (Long seatId : seatIds) {
            String key = LOCK_PREFIX + seatId;
            try {
                Long result = redisTemplate.execute(script, Collections.singletonList(key), lockToken);
                if (result != null && result > 0) {
                    log.debug("Released lock for seatId={}", seatId);
                }
            } catch (Exception e) {
                log.error("Failed to release lock for seatId={}", seatId, e);
            }
        }
    }

    /**
     * Check if a seat is currently locked in Redis.
     */
    public boolean isLocked(Long seatId) {
        String key = LOCK_PREFIX + seatId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Extend the TTL of existing locks (e.g. user is still on payment page).
     */
    public boolean extendLocks(List<Long> seatIds, String lockToken) {
        Duration ttl = Duration.ofMinutes(holdDurationMinutes);
        boolean allExtended = true;

        for (Long seatId : seatIds) {
            String key = LOCK_PREFIX + seatId;
            String currentToken = redisTemplate.opsForValue().get(key);
            if (lockToken.equals(currentToken)) {
                redisTemplate.expire(key, ttl);
            } else {
                allExtended = false;
            }
        }
        return allExtended;
    }

    public List<String> getLockKeys(List<Long> seatIds) {
        return seatIds.stream()
                .map(id -> LOCK_PREFIX + id)
                .collect(Collectors.toList());
    }
}
