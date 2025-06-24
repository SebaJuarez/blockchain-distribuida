package com.blockchain.miningpool.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RedisHash("Miner")
public class Miner {
    @Id
    private String publicKey;
    private Instant lastTimestamp;
    private boolean isGpuMiner;
}
