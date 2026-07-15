package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.repositories.MinersRepository;
import com.blockchain.miningpool.services.MinerScalerService;
import com.blockchain.miningpool.services.MinerService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MinerServiceImpl implements MinerService {

    private static final Logger logger = LoggerFactory.getLogger(MinerServiceImpl.class);
    private Integer lastTargetSize = null;
    private final MinersRepository minersRepository;
    private final MinerScalerService minerScaler;

    public MinerServiceImpl(MinersRepository minersRepository, MinerScalerService minerScaler, MeterRegistry meterRegistry) {
        this.minersRepository = minersRepository;
        this.minerScaler = minerScaler;

        Gauge.builder("mining.pool.miners", this, MinerServiceImpl::getMinersCount)
                .tag("type", "total").register(meterRegistry);
        Gauge.builder("mining.pool.miners", this, m -> m.getMiners().stream().filter(Miner::isGpuMiner).count())
                .tag("type", "gpu").register(meterRegistry);
        Gauge.builder("mining.pool.miners", this, m -> m.getMiners().stream().filter(min -> !min.isGpuMiner()).count())
                .tag("type", "cpu").register(meterRegistry);
    }

    @Override
    public boolean addMiner(Miner miner) {
        if (!miner.isGpuMiner()) return false;
        minersRepository.save(miner);
        return true;
    }

    @Override
    public boolean updateKeepAlive(String minerId) {

        if (minerId == null || minerId.trim().isEmpty()) {
            logger.warn("Id del minero es nulo.");
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
        // 1) Borrar los miners pasados de cutoff
        minersRepository.findAll().forEach(miner -> {
            if (miner.getLastTimestamp().isBefore(cutoff)) {
                minersRepository.delete(miner);
                logger.info("Miner {} fue borrado tras {}ms sin keep-alive", miner.getPublicKey(), keepAliveTimeout);
            }
        });
        // 2) Contar cuántos quedaron
        long remaining = minersRepository.count();
        int targetSize = (remaining == 0) ? 5 : 0;
        logger.debug("Miners vivos: {}. Estado deseado MIG: {}", remaining, targetSize);
        // 3) Si el estado no cambió, no hago nada
        if (lastTargetSize != null && lastTargetSize == targetSize) {
            logger.debug("Ya estaba en {}, no se vuelve a escalar.", targetSize);
            return;
        }
        // 4) Ajustar tamaño del MIG y actualizar flag
        minerScaler.resize(targetSize);
        lastTargetSize = targetSize;
    }

    @Override
    public Long getMinersCount() {
        return minersRepository.count();
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