package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.models.PendingMiningResult;
import com.blockchain.miningpool.repositories.PendingMiningResultRepository;
import com.blockchain.miningpool.services.PendingMiningResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PendingMiningResultServiceImpl
        implements PendingMiningResultService {

    private final PendingMiningResultRepository repository;

    @Override
    public void save(MiningResult miningResult) {

        PendingMiningResult pending =
                PendingMiningResult.builder()
                        .id(UUID.randomUUID())
                        .candidateHash(miningResult.getBlockId())
                        .miningResult(miningResult)
                        .attempts(0)
                        .createdAt(Instant.now())
                        .lastAttempt(null)
                        .build();

        repository.save(pending);
    }

    @Override
    public List<PendingMiningResult> findAll() {

        List<PendingMiningResult> list = new ArrayList<>();

        repository.findAll().forEach(list::add);

        return list;
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteByCandidate(String candidateHash) {

        repository.findByCandidateHash(candidateHash)
                .forEach(repository::delete);

    }
}
