package com.blockchain.miningpool.services;

import com.blockchain.miningpool.dtos.MiningResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

public interface ReliableDeliveryService {

    boolean send(MiningResult miningResult);

    boolean retrySend(MiningResult miningResult);


}
