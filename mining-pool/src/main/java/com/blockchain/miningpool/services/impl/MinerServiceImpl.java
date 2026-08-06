package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.repositories.MinersRepository;
import com.blockchain.miningpool.services.MinerScalerService;
import com.blockchain.miningpool.services.MinerService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.blockchain.miningpool.util.EcUtils;

@Service
public class MinerServiceImpl implements MinerService {

    private static final Logger logger = LoggerFactory.getLogger(MinerServiceImpl.class);
    private static final String MIG_TARGET_SIZE_KEY = "pool:mig-target-size";
    private static final String MIG_RESIZE_LOCK_KEY = "pool:mig-resize-lock";
    public static final String MINING_TASK_ACTIVE_KEY = "pool:mining-task-active";
    private static final Duration MIG_RESIZE_LOCK_TTL = java.time.Duration.ofSeconds(10);

    private final MinersRepository minersRepository;
    private final MinerScalerService minerScaler;
    private final RedisTemplate<String, String> redisTemplate;

    public MinerServiceImpl(MinersRepository minersRepository, MinerScalerService minerScaler,
            MeterRegistry meterRegistry, RedisTemplate<String, String> redisTemplate) {
        this.minersRepository = minersRepository;
        this.minerScaler = minerScaler;
        this.redisTemplate = redisTemplate;

        Gauge.builder("mining.pool.miners", this, MinerServiceImpl::getMinersCount)
                .tag("type", "total").register(meterRegistry);
        Gauge.builder("mining.pool.miners", this, m -> m.getMiners().stream().filter(Miner::isGpuMiner).count())
                .tag("type", "gpu").register(meterRegistry);
        Gauge.builder("mining.pool.miners", this, m -> m.getMiners().stream().filter(min -> !min.isGpuMiner()).count())
                .tag("type", "cpu").register(meterRegistry);
    }

    @Override
    public boolean addMiner(Miner miner) {
        if (!miner.isGpuMiner())return false;
        if (!EcUtils.isValidPublicKeyHex(miner.getPublicKey())) {
            logger.warn("MinerService: registro rechazado, clave pública inválida: {}", miner.getPublicKey());
            return false;
        }
        minersRepository.save(miner);
        logger.info("MinerService: minero registrado: {}", miner.getPublicKey());
        return true;
    }

    @Override
    public boolean updateKeepAlive(String minerId) {

        if (minerId == null || minerId.trim().isEmpty()) {
            logger.warn("MinerService: Id del minero es nulo.");
            return false;
        }

        Optional<Miner> minerOptional = minersRepository.findById(minerId);
        if (minerOptional.isPresent()) {
            Miner miner = minerOptional.get();
            miner.setLastTimestamp(Instant.now());
            minersRepository.save(miner);
            logger.debug("MinerService: Minero {} Keep-alive actualizado.", miner.getPublicKey());
            return true;
        } else {
            logger.warn("MinerService: Minero " + minerId + " no encontrado.");
            return false;
        }
    }

    @Override
    public boolean isMinerExists(String publicKey) {
        return minersRepository.existsById(publicKey);
    }

    @Override
    public void checkKeepAliveMiners(long keepAliveTimeout) {
        Instant cutoff = Instant.now().minusMillis(keepAliveTimeout);
        minersRepository.findAll().forEach(miner -> {
            if (miner.getLastTimestamp().isBefore(cutoff)) {
                minersRepository.delete(miner);
                logger.info("MinerService: Miner {} fue borrado tras {}ms sin keep-alive", miner.getPublicKey(), keepAliveTimeout);
            }
        });

        long remaining = minersRepository.count();
        int targetSize = (remaining == 0) ? computeTargetSizeFromDifficulty() : 0;
        logger.debug("MinerService: Miners vivos: {}. Estado deseado MIG: {}", remaining, targetSize);

        String lastTargetSizeStr = redisTemplate.opsForValue().get(MIG_TARGET_SIZE_KEY);
        Integer lastTargetSize = lastTargetSizeStr != null ? Integer.valueOf(lastTargetSizeStr) : null;
        boolean wouldScaleDown = lastTargetSize != null && targetSize < lastTargetSize;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(MINING_TASK_ACTIVE_KEY)) && wouldScaleDown) {
            logger.info("MinerService: Tarea de minería en curso sin mineros registrados, se omite el resize del MIG.");
            return;
        }


        if (lastTargetSize != null && lastTargetSize == targetSize) {
            logger.debug("MinerService: Ya estaba en {}, no se vuelve a escalar.", targetSize);
            return;
        }

        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(MIG_RESIZE_LOCK_KEY, "1", MIG_RESIZE_LOCK_TTL);
        if (acquiredLock == null || !acquiredLock) {
            logger.debug("MinerService: Otra réplica ya está aplicando el resize del MIG, se omite este ciclo.");
            return;
        }
        try {
            minerScaler.resize(targetSize);
            redisTemplate.opsForValue().set(MIG_TARGET_SIZE_KEY, String.valueOf(targetSize));
        } finally {
            redisTemplate.delete(MIG_RESIZE_LOCK_KEY);
        }
    }

    @Override
    public Long getMinersCount() {
        return minersRepository.count();
    }

    private static final String COORDINATOR_CHALLENGE_KEY = "current_system_challenge";
    private static final int MAX_MIG_SIZE = 5;
    private static final int FALLBACK_ZEROS = 4;

    private int computeTargetSizeFromDifficulty() {
        String challenge = redisTemplate.opsForValue().get(COORDINATOR_CHALLENGE_KEY);
        int zeros = (challenge != null && !challenge.isEmpty()) ? challenge.length() : FALLBACK_ZEROS;
        return Math.max(1, Math.min(zeros, MAX_MIG_SIZE));
    }

    @Override
    public List<Miner> getMiners() {
        return (List<Miner>) minersRepository.findAll();
    }

    @Override
    public Optional<Miner> findById(String id) {
        return minersRepository.findById(id);
    }
}