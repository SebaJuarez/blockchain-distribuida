package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.feingClients.CoordinatorClient;
import com.blockchain.miningpool.services.MiningResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

        HttpStatusCode responseStatus = coordinatorClient.sendResult(miningResult);

        if(responseStatus != HttpStatus.OK || responseStatus != HttpStatus.CREATED) {
            return false;
        }
        return true;
    }
}
