package com.blockchain.miningpool.scheduler;

import com.blockchain.miningpool.models.PendingMiningResult;
import com.blockchain.miningpool.services.PendingMiningResultService;
import com.blockchain.miningpool.services.ReliableDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.blockchain.miningpool.services.PoolAccountingService;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PendingMiningResultScheduler {

    private final PendingMiningResultService pending;
    private final ReliableDeliveryService sender;
    private final PoolAccountingService poolAccountingService;

    @Scheduled(fixedDelay = 5000)
    public void retryPendingResults() {

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