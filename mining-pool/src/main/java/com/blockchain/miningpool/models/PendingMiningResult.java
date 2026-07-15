package com.blockchain.miningpool.models;

import com.blockchain.miningpool.dtos.MiningResult;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.time.Instant;
import java.util.UUID;

@RedisHash(value = "PendingMiningResult", timeToLive = 3600)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingMiningResult {

    @Id
    private UUID id;

    @Indexed
    private String candidateHash;

    private MiningResult miningResult;

    private int attempts;

    private Instant createdAt;

    private Instant lastAttempt;

    private Instant nextRetryAt;

    @Indexed
    private String resultKey;
}