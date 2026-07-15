package com.blockchain.miningpool.repositories;

import com.blockchain.miningpool.models.PendingMiningResult;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingMiningResultRepository
        extends CrudRepository<PendingMiningResult, UUID> {

    Optional<PendingMiningResult> findByResultKey(String resultKey);

    List<PendingMiningResult> findByCandidateHash(String candidateHash);

}