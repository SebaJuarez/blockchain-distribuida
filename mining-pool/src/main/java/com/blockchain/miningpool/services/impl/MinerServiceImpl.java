package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.repositories.MinersRepository;
import com.blockchain.miningpool.services.MinerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinerServiceImpl implements MinerService {

    private final MinersRepository minersRepository;

    @Override
    public void addMiner(Miner miner) {

    }

    @Override
    public void updateKeepAlive(String minerId) {

    }

    @Override
    public void checkKeepAliveMiners() {

    }

    @Override
    public void deleteMiner(Miner miner) {

    }

    @Override
    public void deleteAllMiners() {

    }

    @Override
    public Miner getMiner(String publicKey) {
        return null;
    }

    @Override
    public boolean isMinerExists(String publicKey) {
        return false;
    }

    @Override
    public boolean isGpuMiner(String publicKey) {
        return false;
    }

    @Override
    public int getMinersCount() {
        return 0;
    }
}
