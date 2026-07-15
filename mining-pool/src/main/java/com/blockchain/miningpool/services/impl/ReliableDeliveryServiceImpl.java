package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.dtos.MiningResult;
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

@Service
@RequiredArgsConstructor
public class ReliableDeliveryServiceImpl implements ReliableDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(ReliableDeliveryServiceImpl.class);
    private final CoordinatorClient coordinatorClient;
    private final PendingMiningResultService pendingMiningResultService;

    @Override
    @Retry(name = "coordinator")
    @CircuitBreaker(name = "coordinator", fallbackMethod = "sendFallback")
    public boolean send(MiningResult miningResult) {
        ResponseEntity<String> response = coordinatorClient.sendResult(miningResult);
        return response.getStatusCode().is2xxSuccessful();
    }

    @Override
    @CircuitBreaker(name = "coordinator", fallbackMethod = "retryFallback")
    public boolean retrySend(MiningResult miningResult) {
        ResponseEntity<String> response = coordinatorClient.sendResult(miningResult);
        return response.getStatusCode().is2xxSuccessful();
    }

    public boolean sendFallback(MiningResult miningResult, Exception ex) {
        if (isNonRetryableError(ex)) {
            return false;
        }
        pendingMiningResultService.save(miningResult);
        return false;
    }

    public boolean retryFallback(MiningResult miningResult, Exception ex) {
        logger.warn("Reintento falló para blockId={}, nonce={}: {}",
                miningResult.getBlockId(), miningResult.getNonce(), ex.getMessage());
        return false;
    }

    private boolean isNonRetryableError(Exception ex) {
        return ex instanceof feign.FeignException.BadRequest ||
                ex instanceof feign.FeignException.Forbidden ||
                ex instanceof feign.FeignException.NotFound ||
                ex instanceof feign.FeignException.Unauthorized;
    }

}