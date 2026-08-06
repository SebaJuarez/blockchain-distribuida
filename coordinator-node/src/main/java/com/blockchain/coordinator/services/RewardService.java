package com.blockchain.coordinator.services;

import com.blockchain.coordinator.config.BlockchainConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardService {

    private final BlockchainConfig config;
    private final BalanceService balanceService;

    public double calculateReward(int blockIndex) {
        if (config.isUnlimitedReward()) {
            return 20.0;
        }

        int halvings = blockIndex / config.getHalvingInterval();
        double reward = config.getBaseReward() / Math.pow(2, halvings);
        double remaining = balanceService.getSystemBalance();
        double actual = Math.min(reward, remaining);

        if (actual <= 0) {
            log.info("No more genesis funds available for rewards.");
        }
        return actual;
    }

    public boolean hasFundsForReward() {
        if (config.isUnlimitedReward()) return true;
        return balanceService.getSystemBalance() > 0;
    }
}