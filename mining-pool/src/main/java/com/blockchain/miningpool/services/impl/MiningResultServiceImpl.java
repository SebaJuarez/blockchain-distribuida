package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.config.PoolKeyConfig;
import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.services.MinerService;
import com.blockchain.miningpool.services.MiningResultService;
import com.blockchain.miningpool.services.PoolAccountingService;
import com.blockchain.miningpool.services.ReliableDeliveryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MiningResultServiceImpl implements MiningResultService {

    private final ReliableDeliveryService reliableDeliveryService;
    private final MinerService minerService;
    private final MeterRegistry meterRegistry;
    private final PoolAccountingService poolAccountingService;
    private final PoolKeyConfig poolKeyConfig;


    @Override
    public boolean isValidMiningResult(MiningResult miningResult) {
        if (!StringUtils.hasText(miningResult.getHash()) || !StringUtils.hasText(miningResult.getPrevious_hash()) ||
                !StringUtils.hasText(miningResult.getBlockId()) || miningResult.getData() == null ||
                miningResult.getData().isEmpty() || miningResult.getIndex() < 0 || miningResult.getNonce() < 0 ||
                miningResult.getTimestamp() < 0) {
            return false;
        }

        String originalMinerPublicKey = miningResult.getMinerId();

        boolean isRegisteredMiner = minerService.findById(originalMinerPublicKey).isPresent();

        boolean isGpu = minerService.findById(originalMinerPublicKey).map(Miner::isGpuMiner).orElse(false);
        String hardwareType = isGpu ? "GPU" : "CPU";

        Counter.builder("mining.pool.blocks.submitted")
                .tag("hardware", hardwareType)
                .tag("miner", originalMinerPublicKey)
                .register(meterRegistry).increment();
        Counter.builder("mining.hashes.computed")
                .tag("hardware", hardwareType)
                .tag("miner", originalMinerPublicKey)
                .register(meterRegistry).increment(miningResult.getNonce());

        // El coordinador siempre le paga al pool y el pool reparte el balance interno.
        miningResult.setMinerId(poolKeyConfig.getPublicKeyHex());

        // Solo los mineros registrados acumulan shares. Si el minero no es uno registrado no se reparte nada: el premio queda íntegro en el pool.
        if (isRegisteredMiner) {
            poolAccountingService.recordShare(originalMinerPublicKey);
        }

        Optional<Double> deliveryOutcome = reliableDeliveryService.send(miningResult);
        deliveryOutcome.ifPresent(reward -> {
            poolAccountingService.distributeReward(reward);
            Counter.builder("mining.pool.blocks.accepted")
                    .tag("hardware", hardwareType)
                    .register(meterRegistry).increment();
        });
        return deliveryOutcome.isPresent();
    }
}