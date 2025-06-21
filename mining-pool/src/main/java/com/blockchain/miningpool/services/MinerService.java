package com.blockchain.miningpool.services;

import com.blockchain.miningpool.models.Miner;

public interface MinerService {
    void addMiner(Miner miner);
    void updateKeepAlive(String minerId);
    void checkKeepAliveMiners();
    void deleteMiner(Miner miner);
    void deleteAllMiners();
    Miner getMiner(String publicKey);
    boolean isMinerExists(String publicKey);
    boolean isGpuMiner(String publicKey);
    int getMinersCount();
}
