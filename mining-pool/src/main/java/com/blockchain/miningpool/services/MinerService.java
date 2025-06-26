package com.blockchain.miningpool.services;

import com.blockchain.miningpool.models.Miner;

import java.util.List;
import java.util.Optional;


public interface MinerService {
    boolean addMiner(Miner miner);

    boolean updateKeepAlive(String minerId);
    boolean isMinerExists(String publicKey);

    void checkKeepAliveMiners(long keepAliveTimeout);

    Long getMinersCount();

    List<Miner> getMiners();

    Optional<Miner> findById(String id);
}
