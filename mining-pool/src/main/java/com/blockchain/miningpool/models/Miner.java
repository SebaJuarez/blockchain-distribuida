package com.blockchain.miningpool.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.sql.Timestamp;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RedisHash("Miner")
public class Miner {

    @Id
    private String publicKey;
    private Timestamp lastTimestamp;
    private boolean isGpuMiner;
}
