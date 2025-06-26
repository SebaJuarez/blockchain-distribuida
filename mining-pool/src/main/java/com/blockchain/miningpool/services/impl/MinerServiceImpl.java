package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.repositories.MinersRepository;
import com.blockchain.miningpool.services.MinerService;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MinerServiceImpl implements MinerService {
    private Integer lastTargetSize = null;
    private final MinersRepository minersRepository;
    private final Compute compute;
    @Value("${gcp.project-id}")
    private String projectId;
    @Value("${gcp.compute.zone}")
    private String zone;
    @Value("${gcp.compute.mig-name}")
    private String migName;

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

        // 1) Borrar los miners pasados de cutoff
        minersRepository.findAll().forEach(miner -> {
            if (miner.getLastTimestamp().isBefore(cutoff)) {
                minersRepository.delete(miner);
                System.out.println("Miner " + miner.getPublicKey() +
                        " fue borrado tras " + keepAliveTimeout + "ms sin keep-alive");
            }
        });

        // 2) Contar cuántos quedaron
        long remaining = minersRepository.count();
        int targetSize = (remaining == 0) ? 5 : 0;
        System.out.println("Miners vivos: " + remaining + ". Estado deseado MIG: " + targetSize);

        // 3) Si el estado no cambió, no hago nada
        if (lastTargetSize != null && lastTargetSize == targetSize) {
            System.out.println("Ya estaba en " + targetSize + ", no se vuelve a escalar.");
            return;
        }

        // 4) Ajustar tamaño del MIG y actualizar flag
        try {
            Compute.InstanceGroupManagers.Resize request =
                    compute.instanceGroupManagers()
                            .resize(projectId, zone, migName, targetSize);
            Operation op = request.execute();
            System.out.println("Resize iniciado a " + targetSize + ": " + op.getName());
            lastTargetSize = targetSize;
        } catch (Exception e) {
            System.err.println("Error al redimensionar MIG: " + e.getMessage());
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

    @Override
    public Optional<Miner> findById(String id) {
        return minersRepository.findById(id);
    }
}