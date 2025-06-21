package com.blockchain.miningpool.services;

import com.blockchain.miningpool.dtos.MiningResult;
import org.springframework.stereotype.Service;

public interface MiningResultService {
    boolean isValidMiningResult(MiningResult miningResult);
}
