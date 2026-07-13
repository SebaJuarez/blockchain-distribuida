package com.blockchain.miningpool.scheduler;

import com.blockchain.miningpool.models.PendingMiningResult;
import com.blockchain.miningpool.services.PendingMiningResultService;
import com.blockchain.miningpool.services.ReliableDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PendingMiningResultScheduler {

    private final PendingMiningResultService pending;

    private final ReliableDeliveryService sender;

    @Scheduled(fixedDelay = 5000)
    public void retryPendingResults() {

        for (PendingMiningResult result : pending.findAll()) {

            boolean ok =
                    sender.send(result.getMiningResult());

            if (ok) {
                pending.delete(result.getId());
            }

        }

    }

}
