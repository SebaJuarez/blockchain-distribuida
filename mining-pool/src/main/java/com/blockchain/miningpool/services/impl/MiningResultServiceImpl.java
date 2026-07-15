package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.services.MinerService;
import com.blockchain.miningpool.services.MiningResultService;
import com.blockchain.miningpool.services.ReliableDeliveryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MiningResultServiceImpl implements MiningResultService {

    @Value("${miner.id:ERROR_MARCA_DEPA}")
    private String minerId;
    private final ReliableDeliveryService reliableDeliveryService;
    private final MinerService minerService;
    private final MeterRegistry meterRegistry;

    @Override
    public boolean isValidMiningResult(MiningResult miningResult) {
        if (!StringUtils.hasText(miningResult.getHash()) || !StringUtils.hasText(miningResult.getPrevious_hash()) ||
                !StringUtils.hasText(miningResult.getBlockId()) || miningResult.getData() == null ||
                miningResult.getData().isEmpty() || miningResult.getIndex() < 0 || miningResult.getNonce() < 0 ||
                miningResult.getTimestamp() < 0) {
            return false;
        }

        boolean isGpu = minerService.findById(miningResult.getMinerId()).map(Miner::isGpuMiner).orElse(false);
        String hardwareType = isGpu ? "GPU" : "CPU";

        Counter.builder("mining.pool.blocks.submitted").tag("hardware", hardwareType).register(meterRegistry).increment();
        Counter.builder("mining.hashes.computed").tag("hardware", hardwareType).register(meterRegistry).increment(miningResult.getNonce());

        miningResult.setMinerId(minerId);
        return reliableDeliveryService.send(miningResult);
    }
}