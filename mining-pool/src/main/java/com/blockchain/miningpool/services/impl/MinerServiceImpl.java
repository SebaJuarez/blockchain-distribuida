package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.repositories.MinersRepository;
import com.blockchain.miningpool.services.MinerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MinerServiceImpl implements MinerService {

    private final MinersRepository minersRepository;

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
            System.out.println("Id del minero es nulo.");
            return false;
        }

        Optional<Miner> minerOptional = minersRepository.findById(minerId);

        if (minerOptional.isPresent()) {
            Miner miner = minerOptional.get();
            miner.setLastTimestamp(Instant.now());
            minersRepository.save(miner);
            System.out.println("MinerService: Minero " + miner.getPublicKey() + " Keep-alive actualizado.");
            return true;
        } else {
            System.out.println("MinerService: Minero " + minerId + " no encontrado.");
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

        for (Miner miner : minersRepository.findAll()) {
            if (miner.getLastTimestamp().isBefore(cutoff)) {
                minersRepository.delete(miner);
                System.out.println("Miner " + miner.getPublicKey() +
                        " fue borrado tras " + keepAliveTimeout + "ms sin keep-alive");
            }
        }
    }

    @Override
    public Long getMinersCount() {
        return minersRepository.count();
    }

    @Override
    public List<Miner> getMiners() {
        return (List<Miner>) minersRepository.findAll();
    }
}