package com.blockchain.coordinator.services;

import com.blockchain.coordinator.config.BlockchainConfig;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceService {

    private final RedisTemplate<String, String> redisTemplate;
    private final BlockchainConfig blockchainConfig;

    private static final String BALANCE_PREFIX = "balance:";

    public double getBalance(String publicKey) {
        String val = redisTemplate.opsForValue().get(BALANCE_PREFIX + publicKey);
        return val != null ? Double.parseDouble(val) : 0.0;
    }

    public void setBalance(String publicKey, double amount) {
        redisTemplate.opsForValue().set(BALANCE_PREFIX + publicKey, String.valueOf(amount));
    }

    public void increment(String publicKey, double amount) {
        String key = BALANCE_PREFIX + publicKey;
        double current = getBalance(publicKey);
        double updated = current + amount;
        redisTemplate.opsForValue().set(key, String.valueOf(updated));
    }

    public void decrement(String publicKey, double amount) {
        String key = BALANCE_PREFIX + publicKey;
        double current = getBalance(publicKey);
        double updated = current - amount;
        redisTemplate.opsForValue().set(key, String.valueOf(updated));
    }

    public boolean hasFunds(String publicKey, double amount) {
        return getBalance(publicKey) >= amount;
    }

    public void applyBlock(Block block) {
        for (Transaction tx : block.getData()) {
            String sender = tx.getSender();
            String receiver = tx.getReceiver();
            double amt = tx.getAmount();

            if (sender != null && !sender.isBlank() && !"system".equals(sender)) {
                decrement(sender, amt);
            }
            if (receiver != null && !receiver.isBlank()) {
                increment(receiver, amt);
            }
        }
    }

    public void initGenesisBalance() {
        if (blockchainConfig.isGenesisReward()) {
            setBalance(blockchainConfig.getSystemAddress(), blockchainConfig.getGenesisSupply());
            log.info("Genesis balance initialized: {} for {}",
                    blockchainConfig.getGenesisSupply(), blockchainConfig.getSystemAddress());
        }
    }

    public double getSystemBalance() {
        return getBalance(blockchainConfig.getSystemAddress());
    }
}