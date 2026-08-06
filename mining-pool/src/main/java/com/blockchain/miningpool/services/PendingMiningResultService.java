package com.blockchain.miningpool.services;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.models.PendingMiningResult;

import java.util.List;
import java.util.UUID;

public interface PendingMiningResultService {

    void save(MiningResult result);

    List<PendingMiningResult> findAll();

    void delete(UUID id);

    void deleteByCandidate(String candidateHash);

    void registerFailedAttempt(UUID id);

}
