package com.blockchain.coordinator.scheduler;

import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.services.BlockService;
import com.blockchain.coordinator.services.MiningTaskPublisher;
import com.blockchain.coordinator.services.TransactionPoolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskScheduler {

    private final BlockService blockService;
    private final MiningTaskPublisher miningTaskPublisher;
    private final TransactionPoolService transactionPoolService;

    @Value("${blockchain.mining.transactions-per-block}")
    private int transactionsPerBlock;
    @Value("${blockchain.mining.hash-challenge}")
    private String hashChallenge;

    public TaskScheduler(BlockService blockService, MiningTaskPublisher miningTaskPublisher, TransactionPoolService transactionPoolService) {
        this.blockService = blockService;
        this.miningTaskPublisher = miningTaskPublisher;
        this.transactionPoolService = transactionPoolService;
    }

    @Scheduled(cron = "0 */1 * * * *")
    public void createAndPublishMiningTask() {
        // Verifica si hay suficientes transacciones en el pool para formar un bloque.
        if (transactionPoolService.getPendingTransactionCount() >= transactionsPerBlock) {
            System.out.println("Scheduler: creando nuevo bloque candidato (tarea de mineria)");
            // Crea un nuevo bloque candidato
            Block newBlockCandidate = blockService.createNewMiningCandidateBlock(transactionsPerBlock);
            if (newBlockCandidate != null) {
                // Publica la tarea en RabbitMQ
                miningTaskPublisher.publishMiningTask(newBlockCandidate, hashChallenge);
            } else {
                System.out.println("Scheduler: No hay transacciones suficientes para crear el bloque candidato).");
            }
        } else {
            System.out.println("Scheduler: No hay transacciones suficientes para crear el bloque candidato. Actualmente: " + transactionPoolService.getPendingTransactionCount() + ". Requerido: " + transactionsPerBlock);
        }
    }
}