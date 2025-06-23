package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.feingClients.CoordinatorClient;
import com.blockchain.miningpool.services.MiningResultService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MiningResultServiceImpl implements MiningResultService {

    private final CoordinatorClient coordinatorClient;

    @Override
    public boolean isValidMiningResult(MiningResult miningResult) {
        if (miningResult.getHash().isEmpty() ||
                miningResult.getPrevious_hash().isEmpty() ||
                miningResult.getData().isEmpty() ||
                miningResult.getIndex() < 0 ||
                miningResult.getNonce() < 0 ||
                miningResult.getTimestamp() < 0 ||
                miningResult.getBlockId().isEmpty() ||
                miningResult.getMinerId().isEmpty())
            return false;
        try {
            ResponseEntity<String> resp = coordinatorClient.sendResult(miningResult);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (FeignException.BadRequest e) {
            return false;
        }
    }
}