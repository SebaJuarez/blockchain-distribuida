package com.blockchain.coordinator.scheduler;

import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.services.BlockService;
import com.blockchain.coordinator.services.MiningTaskPublisher;
import com.blockchain.coordinator.services.TransactionPoolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("blockchainTaskScheduler")
public class TaskScheduler {

    private final BlockService blockService;
    private final MiningTaskPublisher miningTaskPublisher;
    private final TransactionPoolService transactionPoolService;

    @Value("${blockchain.mining.transactions-per-block}")
    private int transactionsPerBlock;
    @Value("${blockchain.mining.max-transactions-per-block}")
    private int maxTransactionsPerBlock;
    @Value("${blockchain.mining.hash-challenge}")
    private String hashChallenge;

    public TaskScheduler(BlockService blockService, MiningTaskPublisher miningTaskPublisher, TransactionPoolService transactionPoolService) {
        this.blockService = blockService;
        this.miningTaskPublisher = miningTaskPublisher;
        this.transactionPoolService = transactionPoolService;
    }

    @Scheduled(cron = "0 */1 * * * *")
    public void createAndPublishMiningTask() {
        // 1) Si ya hay una tarea previa (task!=null):
        MiningTask prev = miningTaskPublisher.getCurrentTask();
        boolean inProgress = blockService.hasBlocksInProgress();

        if (prev != null) {
            if (prev.getRetries() >= 3) {
                if (inProgress) {
                    System.out.println("Se superaron 3 retries y siguen bloques en progreso. Descarto candidato anterior.");
                    blockService.clearBlocksInProgress();
                }
            } else {
                if (inProgress) {
                    // tiro la misma tarea otra vez, sumando retry
                    miningTaskPublisher.incrementRetries();
                    System.out.println("Reintentando tarea de minería (retry " + prev.getRetries() + ")");
                    return;
                }
            }
        }

        // Si hay transacciones suficientes, creo candidato nuevo
        int pending = transactionPoolService.getPendingTransactionCount();
        if (pending >= transactionsPerBlock) {
            System.out.println("Scheduler: creando nuevo bloque candidato (tarea de mineria)");
            Block newBlock = blockService.createNewMiningCandidateBlock(maxTransactionsPerBlock);
            if (newBlock != null) {
                miningTaskPublisher.publishMiningTask(newBlock, hashChallenge);
            } else {
                System.out.println("Scheduler: No hay transacciones suficientes para crear el bloque candidato).");
            }
        } else {
            System.out.println("Scheduler: No hay transacciones suficientes para crear el bloque candidato. Actualmente: "
                + pending + ". Requerido: " + transactionsPerBlock);
        }
    }
}