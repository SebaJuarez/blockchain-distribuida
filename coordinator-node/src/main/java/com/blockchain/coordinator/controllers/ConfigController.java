package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.config.BlockchainConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ConfigController {

    private final BlockchainConfig config;

    @GetMapping
    public Map<String, Object> getConfig() {
        return Map.of(
                "mode", config.getMode(),
                "rewardSource", config.getRewardSource(),
                "systemAddress", config.getSystemAddress(),
                "genesisSupply", config.getGenesisSupply(),
                "baseReward", config.getBaseReward(),
                "halvingInterval", config.getHalvingInterval()
        );
    }
}