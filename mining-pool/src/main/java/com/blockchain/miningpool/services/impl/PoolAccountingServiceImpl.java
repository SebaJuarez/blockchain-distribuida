package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.models.PoolBalance;
import com.blockchain.miningpool.repositories.PoolBalanceRepository;
import com.blockchain.miningpool.services.PoolAccountingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PoolAccountingServiceImpl implements PoolAccountingService {

    private static final Logger logger = LoggerFactory.getLogger(PoolAccountingServiceImpl.class);
    private static final String SHARE_PREFIX = "shares:";
    private static final String SHARE_TOTAL_KEY = "shares:total";
    private static final String DISTRIBUTION_LOCK_KEY = "shares:distribution-lock";
    private static final Duration DISTRIBUTION_LOCK_TTL = Duration.ofSeconds(10);

    private final RedisTemplate<String, String> redisTemplate;
    private final PoolBalanceRepository poolBalanceRepository;

    @Override
    public void recordShare(String minerPublicKey) {
        if (minerPublicKey == null || minerPublicKey.isBlank()) {
            logger.warn("PoolAccountingService: share con minerPublicKey vacío/nulo, ignorado.");
            return;
        }
        redisTemplate.opsForValue().increment(SHARE_PREFIX + minerPublicKey);
        redisTemplate.opsForValue().increment(SHARE_TOTAL_KEY);
        logger.debug("PoolAccountingService: share registrado para {}", minerPublicKey);
    }

    @Override
    public void distributeReward(double totalReward) {
        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(DISTRIBUTION_LOCK_KEY, "1", DISTRIBUTION_LOCK_TTL);
        if (acquiredLock == null || !acquiredLock) {
            logger.debug("PoolAccountingService: otra réplica ya está repartiendo la recompensa, se omite.");
            return;
        }

        try {
            doDistributeReward(totalReward);
        } finally {
            redisTemplate.delete(DISTRIBUTION_LOCK_KEY);
        }
    }

    private void doDistributeReward(double totalReward) {
        Set<String> shareKeys = redisTemplate.keys(SHARE_PREFIX + "*");
        if (shareKeys == null || shareKeys.isEmpty()) {
            logger.warn("PoolAccountingService: bloque ganado pero sin shares registrados. " +
                    "Recompensa de {} sin repartir.", totalReward);
            return;
        }

        Map<String, Long> sharesByMiner = new HashMap<>();
        long totalShares = 0L;

        for (String key : shareKeys) {
            String raw = redisTemplate.opsForValue().getAndSet(key, "0");

            if (key.equals(SHARE_TOTAL_KEY)) continue;

            long shares = raw != null ? Long.parseLong(raw) : 0L;
            if (shares <= 0) continue;
            String minerPublicKey = key.substring(SHARE_PREFIX.length());
            sharesByMiner.put(minerPublicKey, shares);
            totalShares += shares;
        }

        if (totalShares == 0) {
            logger.warn("PoolAccountingService: shares totales en 0 al momento del reparto.");
            return;
        }

        for (Map.Entry<String, Long> entry : sharesByMiner.entrySet()) {
            String minerPublicKey = entry.getKey();
            long shares = entry.getValue();
            double portion = (shares / (double) totalShares) * totalReward;

            PoolBalance balance = poolBalanceRepository.findById(minerPublicKey)
                    .orElse(new PoolBalance(minerPublicKey, 0.0));
            balance.setAmount(balance.getAmount() + portion);
            poolBalanceRepository.save(balance);

            logger.info("PoolAccountingService: {} recibe {} ({}/{} shares).",
                    minerPublicKey, portion, shares, totalShares);
        }

        logger.info("PoolAccountingService: reparto completado, {} shares procesados.", totalShares);
    }

    @Override
    public double getBalance(String minerPublicKey) {
        return poolBalanceRepository.findById(minerPublicKey)
                .map(PoolBalance::getAmount)
                .orElse(0.0);
    }
}