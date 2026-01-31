package com.blockchain.coordinator.services;

import com.blockchain.coordinator.dtos.MiningTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class CurrentMiningTaskService {

    private static final Logger logger = LoggerFactory.getLogger(CurrentMiningTaskService.class);

    private static final String CURRENT_MINING_TASK_KEY = "mining:current_task";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MiningTaskPublisher miningTaskPublisher;


    @PostConstruct
    public void init() {
        logger.info("Initializing. Loading previous task from Redis if exists...");
        try {
            String taskData = stringRedisTemplate.opsForValue().get(CURRENT_MINING_TASK_KEY);
            if (taskData != null && !taskData.isEmpty()) {
                MiningTask loadedTask = objectMapper.readValue(taskData, MiningTask.class);
                logger.info("Previous mining task found in Redis: Block {} (Retries: {}).", 
                          loadedTask.getBlock().getHash(), loadedTask.getRetries());
            } else {
                logger.info("No previous mining task found in Redis.");
            }
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing MiningTask from Redis during initialization: {}", e.getMessage(), e);
            stringRedisTemplate.delete(CURRENT_MINING_TASK_KEY);
        }
    }

    public void saveCurrentTask(MiningTask task) {
        try {
            String taskJson = objectMapper.writeValueAsString(task);
            stringRedisTemplate.opsForValue().set(CURRENT_MINING_TASK_KEY, taskJson);
            logger.debug("Current task saved to Redis: {}", task.getBlock().getHash());
        } catch (JsonProcessingException e) {
            logger.error("Error serializing MiningTask to Redis: {}", e.getMessage(), e);
        }
    }

    public MiningTask getCurrentTask() {
        try {
            String taskData = stringRedisTemplate.opsForValue().get(CURRENT_MINING_TASK_KEY);
            if (taskData != null && !taskData.isEmpty()) {
                return objectMapper.readValue(taskData, MiningTask.class);
            }
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing MiningTask from Redis in getCurrentTask(): {}", e.getMessage(), e);
            stringRedisTemplate.delete(CURRENT_MINING_TASK_KEY);
        }
        return null;
    }

    public void incrementCurrentTaskRetries() {
        MiningTask currentTask = getCurrentTask();
        if (currentTask != null) {
            currentTask.setRetries(currentTask.getRetries() + 1);
            saveCurrentTask(currentTask); // Guardar la tarea actualizada
            logger.debug("Incremented retries for {} to {}", currentTask.getBlock().getHash(), currentTask.getRetries());
        }
    }

    public void clearCurrentTask() {
        stringRedisTemplate.delete(CURRENT_MINING_TASK_KEY);
        logger.debug("Current task removed from Redis.");
    }
}