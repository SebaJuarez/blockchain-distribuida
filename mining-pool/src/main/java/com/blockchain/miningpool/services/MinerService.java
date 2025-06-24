package com.blockchain.miningpool.services;

import com.blockchain.miningpool.models.Miner;

import java.util.List;


public interface MinerService {
    boolean addMiner(Miner miner);

    boolean updateKeepAlive(String minerId);
    boolean isMinerExists(String publicKey);

    void checkKeepAliveMiners(long keepAliveTimeout);

    Long getMinersCount();

    List<Miner> getMiners();
}
