package com.blockchain.miningpool.repositories;

import com.blockchain.miningpool.models.PendingMiningResult;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface PendingMiningResultRepository
        extends CrudRepository<PendingMiningResult, UUID> {

    List<PendingMiningResult> findByCandidateHash(String candidateHash);

}