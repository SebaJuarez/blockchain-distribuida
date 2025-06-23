package com.blockchain.coordinator.scheduler;

import com.blockchain.coordinator.dtos.MiningTask;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.ExchangeEvent;
import com.blockchain.coordinator.services.BlockService;
import com.blockchain.coordinator.services.CurrentMiningTaskService;
import com.blockchain.coordinator.services.DifficultyService;
import com.blockchain.coordinator.services.MiningTaskNotifier;
import com.blockchain.coordinator.services.QueueAdminService;
import com.blockchain.coordinator.services.TransactionPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("blockchainTaskScheduler")
@RequiredArgsConstructor
public class TaskScheduler {

    private final BlockService blockService;
    private final MiningTaskNotifier miningTaskNotifier;
    private final CurrentMiningTaskService currentMiningTaskService;
    private final QueueAdminService queueAdminService;
    private final TransactionPoolService transactionPoolService;
    private final DifficultyService difficultyService;

    @Value("${blockchain.mining.max-transactions-per-block}")
    private int maxTransactionsPerBlock;
    @Value("${blockchain.mining.min-transactions-per-block}")
    private int minTransactionsPerBlock;
    @Value("${blockchain.mining.max-retries}")
    private int maxRetries;

    @Scheduled(cron = "${blockchain.mining.task-publication-cron}")
    public void createAndPublishMiningTask() {

        MiningTask prevTask = currentMiningTaskService.getCurrentTask();
        boolean coordinatorHasActiveMiningTask = (prevTask != null);

        if (coordinatorHasActiveMiningTask) {
            if (prevTask.getRetries() >= maxRetries) {
                System.out.println("Scheduler: Se superaron " + maxRetries + " reintentos para el bloque " + prevTask.getBlock().getHash() + ". Descartando candidato.");
                difficultyService.decrementChallenge();
                miningTaskNotifier.notifyMiningTaskDropped(prevTask.getBlock().getHash());
                currentMiningTaskService.clearCurrentTask();
                queueAdminService.purgeBlocksQueue();
            } else {
                currentMiningTaskService.incrementCurrentTaskRetries();
                System.out.println("Scheduler: Tarea de minería (" + prevTask.getBlock().getHash() + ") persistente, reintentos: " + prevTask.getRetries() + ".");
                return;
            }
        }

        int pending = transactionPoolService.getPendingTransactionCount();

        if (pending >= minTransactionsPerBlock) {
            System.out.println("Scheduler: No hay tarea activa o la anterior fue descartada. Creando y publicando nuevo bloque candidato (tarea de minería).");
            Block newBlock = blockService.createNewMiningCandidateBlock(maxTransactionsPerBlock);
            if (newBlock != null) {
                String challengeForNewTask = difficultyService.getCurrentChallenge();
                MiningTask newTask = new MiningTask(ExchangeEvent.NEW_CANDIDATE_BLOCK, challengeForNewTask, newBlock, 0);
                currentMiningTaskService.saveCurrentTask(newTask);
                miningTaskNotifier.notifyNewMiningTask(newBlock, challengeForNewTask, 0);
            } else {
                System.out.println("Scheduler: No hay transacciones suficientes para crear el bloque candidato.");
            }
        } else {
            System.out.println("Scheduler: No hay transacciones suficientes para crear el bloque candidato. Actualmente: "
                    + pending + ". Requerido: " + minTransactionsPerBlock);
        }
    }
}