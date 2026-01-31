package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.repositories.MinersRepository;
import com.blockchain.miningpool.services.MinerScalerService;
import com.blockchain.miningpool.services.MinerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MinerServiceImpl implements MinerService {
    
    private static final Logger logger = LoggerFactory.getLogger(MinerServiceImpl.class);
    
    private Integer lastTargetSize = null;
    private final MinersRepository minersRepository;
    private final MinerScalerService minerScaler;

    @Override
    public boolean addMiner(Miner miner) {
        if (!miner.isGpuMiner()) {
            return false;
        }
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
            logger.debug("MinerService: Minero " + miner.getPublicKey() + " Keep-alive actualizado.");
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
                logger.info("Miner " + miner.getPublicKey() +
                        " fue borrado tras " + keepAliveTimeout + "ms sin keep-alive");
            }
        });

        // 2) Contar cuántos quedaron
        long remaining = minersRepository.count();
        int targetSize = (remaining == 0) ? 5 : 0;
        logger.debug("Miners vivos: " + remaining + ". Estado deseado MIG: " + targetSize);

        // 3) Si el estado no cambió, no hago nada
        if (lastTargetSize != null && lastTargetSize == targetSize) {
            logger.debug("Ya estaba en " + targetSize + ", no se vuelve a escalar.");
            return;
        }

        // 4) Ajustar tamaño del MIG y actualizar flag
        minerScaler.resize(targetSize);
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