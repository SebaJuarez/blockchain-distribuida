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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("blockchainTaskScheduler")
@RequiredArgsConstructor
public class TaskScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);

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
                logger.warn("Scheduler: Se superaron {} reintentos para el bloque {}. Descartando candidato.", maxRetries, prevTask.getBlock().getHash());
                difficultyService.decrementChallenge();
                miningTaskNotifier.notifyMiningTaskDropped(prevTask.getBlock().getHash());
                currentMiningTaskService.clearCurrentTask();
                queueAdminService.purgeBlocksQueue();
            } else {
                currentMiningTaskService.incrementCurrentTaskRetries();
                logger.info("Scheduler: Tarea de minería ({}) persistente, reintentos: {}.", prevTask.getBlock().getHash(), prevTask.getRetries());
                return;
            }
        }

        int pending = transactionPoolService.getPendingTransactionCount();

        if (pending >= minTransactionsPerBlock) {
            logger.info("Scheduler: No hay tarea activa o la anterior fue descartada. Creando y publicando nuevo bloque candidato (tarea de minería).");
            Block newBlock = blockService.createNewMiningCandidateBlock(maxTransactionsPerBlock);
            if (newBlock != null) {
                String challengeForNewTask = difficultyService.getCurrentChallenge();
                MiningTask newTask = new MiningTask(ExchangeEvent.NEW_CANDIDATE_BLOCK, challengeForNewTask, newBlock, 0);
                currentMiningTaskService.saveCurrentTask(newTask);
                miningTaskNotifier.notifyNewMiningTask(newBlock, challengeForNewTask, 0);
            } else {
                logger.warn("Scheduler: No hay transacciones suficientes para crear el bloque candidato.");
            }
        } else {
            logger.info("Scheduler: No hay transacciones suficientes para crear el bloque candidato. Actualmente: {}. Requerido: {}", pending, minTransactionsPerBlock);
        }
    }
}