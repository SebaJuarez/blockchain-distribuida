package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.services.MiningResultService;
import org.springframework.stereotype.Service;

@Service
public class MiningResultServiceImpl implements MiningResultService {



    @Override
    public boolean isValidMiningResult(MiningResult miningResult) {
        return false;
    }
}
