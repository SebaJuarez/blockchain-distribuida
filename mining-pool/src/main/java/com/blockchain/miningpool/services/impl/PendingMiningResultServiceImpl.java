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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PendingMiningResultServiceImpl
        implements PendingMiningResultService {

    private final PendingMiningResultRepository repository;

    @Override
    public void save(MiningResult miningResult) {

        String resultKey =
                miningResult.getBlockId()
                        + "-"
                        + miningResult.getNonce()
                        + "-"
                        + miningResult.getMinerId();

        Optional<PendingMiningResult> existing =
                repository.findByResultKey(resultKey);

        if (existing.isPresent()) {

            PendingMiningResult pending = existing.get();

            pending.setAttempts(
                    pending.getAttempts() + 1);

            pending.setLastAttempt(
                    Instant.now());

            pending.setNextRetryAt(
                    calculateNextRetry(
                            pending.getAttempts()));

            repository.save(pending);

            return;
        }

        PendingMiningResult pending =
                PendingMiningResult.builder()

                        .id(UUID.randomUUID())

                        .candidateHash(
                                miningResult.getBlockId())

                        .resultKey(resultKey)

                        .miningResult(miningResult)

                        .attempts(1)

                        .createdAt(Instant.now())

                        .lastAttempt(Instant.now())

                        .nextRetryAt(
                                calculateNextRetry(1))

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

    private Instant calculateNextRetry(int attempts) {

        long seconds =
                Math.min(100,
                        (long) Math.pow(2, attempts) * 30);

        return Instant.now()
                .plusSeconds(seconds);
    }

    @Override
    public void registerFailedAttempt(UUID id) {
        repository.findById(id).ifPresent(p -> {
            p.setAttempts(p.getAttempts() + 1);
            p.setLastAttempt(Instant.now());
            p.setNextRetryAt(calculateNextRetry(p.getAttempts()));
            repository.save(p);
        });
    }
}
