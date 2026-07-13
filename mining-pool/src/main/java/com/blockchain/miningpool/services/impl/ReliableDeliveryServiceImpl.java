package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.feingClients.CoordinatorClient;
import com.blockchain.miningpool.services.PendingMiningResultService;
import com.blockchain.miningpool.services.ReliableDeliveryService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReliableDeliveryServiceImpl implements ReliableDeliveryService {

    private final CoordinatorClient coordinatorClient;
    private final PendingMiningResultService pendingMiningResultService;

    @Retry(name = "coordinator")
    @CircuitBreaker(
            name = "coordinator",
            fallbackMethod = "fallback"
    )
    @Override
    public boolean send(MiningResult miningResult) {

        ResponseEntity<String> response =
                coordinatorClient.sendResult(miningResult);

        return response.getStatusCode().is2xxSuccessful();
    }

    @Override
    public boolean fallback(
            MiningResult miningResult,
            Exception ex
    ) {

        pendingMiningResultService.save(miningResult);

        return false;
    }

}