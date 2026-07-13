package com.blockchain.miningpool.services;

import com.blockchain.miningpool.dtos.MiningResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

public interface ReliableDeliveryService {
    @Retry(name = "coordinator")
    @CircuitBreaker(
            name = "coordinator",
            fallbackMethod = "fallback"
    )
    boolean send(MiningResult miningResult);

    boolean fallback(
            MiningResult miningResult,
            Exception ex
    );
}
