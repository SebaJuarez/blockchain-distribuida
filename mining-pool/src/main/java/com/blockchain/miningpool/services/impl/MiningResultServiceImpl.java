package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.feingClients.CoordinatorClient;
import com.blockchain.miningpool.services.MiningResultService;
import com.blockchain.miningpool.services.PendingMiningResultService;
import com.blockchain.miningpool.services.ReliableDeliveryService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MiningResultServiceImpl implements MiningResultService {

    @Value("${miner.id:ERROR_MARCA_DEPA}")
    private String minerId;

    private final ReliableDeliveryService reliableDeliveryService;

    @Override
    public boolean isValidMiningResult(MiningResult miningResult) {

        if (!StringUtils.hasText(miningResult.getHash()) ||
                !StringUtils.hasText(miningResult.getPrevious_hash()) ||
                !StringUtils.hasText(miningResult.getBlockId()) ||
                miningResult.getData() == null ||
                miningResult.getData().isEmpty() ||
                miningResult.getIndex() < 0 ||
                miningResult.getNonce() < 0 ||
                miningResult.getTimestamp() < 0) {
            return false;
        }

        miningResult.setMinerId(minerId);

        return reliableDeliveryService.send(miningResult);
    }

}