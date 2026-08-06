package com.blockchain.miningpool.services;

import com.blockchain.miningpool.dtos.MiningResult;

import java.util.Optional;

public interface ReliableDeliveryService {

    Optional<Double> send(MiningResult miningResult);

    Optional<Double> retrySend(MiningResult miningResult);

}