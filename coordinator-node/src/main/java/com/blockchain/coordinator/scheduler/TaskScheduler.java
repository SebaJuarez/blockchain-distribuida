package com.blockchain.coordinator.scheduler;

import com.blockchain.coordinator.dtos.MiningTask;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.ExchangeEvent;
import com.blockchain.coordinator.services.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component("blockchainTaskScheduler")
@RequiredArgsConstructor
public class TaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);
    private static final String SCHEDULER_LOCK_KEY = "scheduler:task-publication-lock";
    private static final Duration SCHEDULER_LOCK_TTL = Duration.ofSeconds(8);

    private final BlockService blockService;
    private final MiningTaskNotifier miningTaskNotifier;
    private final CurrentMiningTaskService currentMiningTaskService;
    private final QueueAdminService queueAdminService;
    private final TransactionPoolService transactionPoolService;
    private final DifficultyService difficultyService;
    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${blockchain.mining.max-transactions-per-block}")
    private int maxTransactionsPerBlock;
    @Value("${blockchain.mining.min-transactions-per-block}")
    private int minTransactionsPerBlock;
    @Value("${blockchain.mining.max-retries}")
    private int maxRetries;

    @Scheduled(cron = "${blockchain.mining.task-publication-cron}")
    public void createAndPublishMiningTask() {
        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(SCHEDULER_LOCK_KEY, "1", SCHEDULER_LOCK_TTL);
        if (acquiredLock == null || !acquiredLock) {
            logger.debug("Scheduler: otra réplica ya está procesando este ciclo, se omite.");
            return;
        }

        try {
            runSchedulerCycle();
        } finally {
            redisTemplate.delete(SCHEDULER_LOCK_KEY);
        }
    }

    private void runSchedulerCycle() {
        MiningTask prevTask = currentMiningTaskService.getCurrentTask();
        boolean coordinatorHasActiveMiningTask = (prevTask != null);

        if (coordinatorHasActiveMiningTask) {
            if (prevTask.getRetries() >= maxRetries) {
                logger.warn("Scheduler: Se superaron reintentos. Descartando candidato.");
                Counter.builder("mining.blocks.discarded").register(meterRegistry).increment();
                difficultyService.decrementChallenge();
                miningTaskNotifier.notifyMiningTaskDropped(prevTask.getBlock().getHash());
                currentMiningTaskService.clearCurrentTask();
                queueAdminService.purgeBlocksQueue();
            } else {
                currentMiningTaskService.incrementCurrentTaskRetries();
                Counter.builder("mining.blocks.retried").register(meterRegistry).increment();
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
                MiningTask newTask = new MiningTask(ExchangeEvent.NEW_CANDIDATE_BLOCK, challengeForNewTask, newBlock, 0, System.currentTimeMillis());
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