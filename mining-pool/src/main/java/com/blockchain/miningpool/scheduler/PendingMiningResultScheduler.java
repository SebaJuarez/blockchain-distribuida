package com.blockchain.miningpool.scheduler;

import com.blockchain.miningpool.models.PendingMiningResult;
import com.blockchain.miningpool.services.PendingMiningResultService;
import com.blockchain.miningpool.services.PoolAccountingService;
import com.blockchain.miningpool.services.ReliableDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PendingMiningResultScheduler {

    private static final String RETRY_LOCK_KEY = "pending-results:retry-lock";
    private static final Duration RETRY_LOCK_TTL = Duration.ofSeconds(4);

    private final PendingMiningResultService pending;
    private final ReliableDeliveryService sender;
    private final PoolAccountingService poolAccountingService;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(fixedDelay = 5000)
    public void retryPendingResults() {
        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(RETRY_LOCK_KEY, "1", RETRY_LOCK_TTL);
        if (acquiredLock == null || !acquiredLock) {
            return; // otra réplica ya está corriendo este ciclo
        }

        try {
            runRetryCycle();
        } finally {
            redisTemplate.delete(RETRY_LOCK_KEY);
        }
    }

    private void runRetryCycle() {
        int processed = 0;
        Instant now = Instant.now();

        for (PendingMiningResult result : pending.findAll()) {

            if (processed >= 20) {
                break;
            }

            if (result.getNextRetryAt() != null &&
                    result.getNextRetryAt().isAfter(now)) {
                continue;
            }

            if (result.getAttempts() >= 20) {
                pending.delete(result.getId());
                continue;
            }

            Optional<Double> deliveryOutcome = sender.retrySend(result.getMiningResult());
            processed++;

            if (deliveryOutcome.isPresent()) {
                pending.delete(result.getId());
                poolAccountingService.distributeReward(deliveryOutcome.get());
            } else {
                pending.registerFailedAttempt(result.getId());
            }
        }
    }
}