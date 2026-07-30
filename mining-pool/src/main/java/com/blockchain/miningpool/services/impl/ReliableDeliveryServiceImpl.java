package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.dtos.MiningResultResponse;
import com.blockchain.miningpool.feingClients.CoordinatorClient;
import com.blockchain.miningpool.services.PendingMiningResultService;
import com.blockchain.miningpool.services.ReliableDeliveryService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReliableDeliveryServiceImpl implements ReliableDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(ReliableDeliveryServiceImpl.class);
    private final CoordinatorClient coordinatorClient;
    private final PendingMiningResultService pendingMiningResultService;

    @Override
    @Retry(name = "coordinator")
    @CircuitBreaker(name = "coordinator", fallbackMethod = "sendFallback")
    public Optional<Double> send(MiningResult miningResult) {
        ResponseEntity<MiningResultResponse> response = coordinatorClient.sendResult(miningResult);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return Optional.of(response.getBody().getReward());
        }
        return Optional.empty();
    }

    @Override
    @CircuitBreaker(name = "coordinator", fallbackMethod = "retryFallback")
    public Optional<Double> retrySend(MiningResult miningResult) {
        ResponseEntity<MiningResultResponse> response = coordinatorClient.sendResult(miningResult);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return Optional.of(response.getBody().getReward());
        }
        return Optional.empty();
    }

    public Optional<Double> sendFallback(MiningResult miningResult, Exception ex) {
        if (isNonRetryableError(ex)) {
            return Optional.empty();
        }
        pendingMiningResultService.save(miningResult);
        return Optional.empty();
    }

    public Optional<Double> retryFallback(MiningResult miningResult, Exception ex) {
        logger.warn("Reintento falló para blockId={}, nonce={}: {}",
                miningResult.getBlockId(), miningResult.getNonce(), ex.getMessage());
        return Optional.empty();
    }

    private boolean isNonRetryableError(Exception ex) {
        return ex instanceof feign.FeignException.BadRequest ||
                ex instanceof feign.FeignException.Forbidden ||
                ex instanceof feign.FeignException.NotFound ||
                ex instanceof feign.FeignException.Unauthorized;
    }

}