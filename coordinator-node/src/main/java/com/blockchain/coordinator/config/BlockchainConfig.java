package com.blockchain.coordinator.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Slf4j
public class BlockchainConfig {

    @Value("${blockchain.mode:testing}")
    private String mode;

    @Value("${blockchain.reward.source:genesis}")
    private String rewardSource;

    @Value("${blockchain.genesis.supply:21000000}")
    private double genesisSupply;

    @Value("${blockchain.genesis.base-reward:50}")
    private double baseReward;

    @Value("${blockchain.genesis.halving-interval:10}")
    private int halvingInterval;

    @Value("${blockchain.genesis.system-address:SYSTEM_GENESIS}")
    private String systemAddress;

    public boolean isTesting() {
        return "testing".equalsIgnoreCase(mode);
    }

    public boolean isProduction() {
        return "production".equalsIgnoreCase(mode);
    }

    public boolean isGenesisReward() {
        return "genesis".equalsIgnoreCase(rewardSource);
    }

    public boolean isUnlimitedReward() {
        return "unlimited".equalsIgnoreCase(rewardSource);
    }

    @PostConstruct
    public void logConfig() {
        log.info("Blockchain mode={}, rewardSource={}, genesisSupply={}", mode, rewardSource, genesisSupply);
    }
}
