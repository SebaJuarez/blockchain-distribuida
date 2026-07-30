package com.blockchain.miningpool.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RedisHash("PoolBalance")
public class PoolBalance {
    @Id
    private String minerPublicKey;
    private double amount;
}