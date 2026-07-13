package com.blockchain.miningpool.models;

import com.blockchain.miningpool.dtos.MiningResult;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.Instant;
import java.util.UUID;

@RedisHash("PendingMiningResult")
@Getter
@Builder
public class PendingMiningResult {

    @Id
    private UUID id;

    private String candidateHash;

    private MiningResult miningResult;

    private int attempts;

    private Instant createdAt;

    private Instant lastAttempt;
}