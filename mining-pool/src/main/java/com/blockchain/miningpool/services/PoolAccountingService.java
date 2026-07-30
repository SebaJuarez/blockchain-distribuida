package com.blockchain.miningpool.services;

public interface PoolAccountingService {

    /**
     * Registra un "share": el minero envió un resultado al pool.
     * NO es PoW parcial verificable — cada resultado enviado cuenta +1,
     */
    void recordShare(String minerPublicKey);

    /**
     * Reparte totalReward proporcionalmente a los shares acumulados desde
     * el último reparto, y los resetea a cero.
     */
    void distributeReward(double totalReward);

    double getBalance(String minerPublicKey);
}