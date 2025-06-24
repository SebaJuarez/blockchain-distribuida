package com.blockchain.coordinator.services;

import com.blockchain.coordinator.dtos.MiningTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentMiningTaskService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MiningTaskPublisher miningTaskPublisher;

    private static final String CURRENT_MINING_TASK_KEY = "current_mining_task";


    @PostConstruct
    public void init() {
        System.out.println("CurrentMiningTaskService: Inicializando. Cargando tarea previa de Redis si existe...");
        try {
            String taskJson = redisTemplate.opsForValue().get(CURRENT_MINING_TASK_KEY);
            if (taskJson != null && !taskJson.isEmpty()) {
                MiningTask loadedTask = objectMapper.readValue(taskJson, MiningTask.class);
                System.out.println("CurrentMiningTaskService: Tarea previa encontrada en Redis: Bloque " + loadedTask.getBlock().getHash() + " (Reintentos: " + loadedTask.getRetries() + ").");
            } else {
                System.out.println("CurrentMiningTaskService: No se encontró tarea de minería previa en Redis.");
            }
        } catch (JsonProcessingException e) {
            System.err.println("CurrentMiningTaskService: Error al deserializar MiningTask desde Redis durante la inicialización: " + e.getMessage());
            redisTemplate.delete(CURRENT_MINING_TASK_KEY);
        }
    }

    public void saveCurrentTask(MiningTask task) {
        try {
            String taskJson = objectMapper.writeValueAsString(task);
            redisTemplate.opsForValue().set(CURRENT_MINING_TASK_KEY, taskJson);
            System.out.println("CurrentMiningTaskService: Tarea actual guardada en Redis: " + task.getBlock().getHash());
        } catch (JsonProcessingException e) {
            System.err.println("CurrentMiningTaskService: Error al serializar MiningTask para Redis: " + e.getMessage());
        }
    }

    public MiningTask getCurrentTask() {
        try {
            String taskJson = redisTemplate.opsForValue().get(CURRENT_MINING_TASK_KEY);
            if (taskJson != null && !taskJson.isEmpty()) {
                return objectMapper.readValue(taskJson, MiningTask.class);
            }
        } catch (JsonProcessingException e) {
            System.err.println("CurrentMiningTaskService: Error al deserializar MiningTask desde Redis en getCurrentTask(): " + e.getMessage());
            redisTemplate.delete(CURRENT_MINING_TASK_KEY);
        }
        return null;
    }

    public void incrementCurrentTaskRetries() {
        MiningTask currentTask = getCurrentTask();
        if (currentTask != null) {
            currentTask.setRetries(currentTask.getRetries() + 1);
            saveCurrentTask(currentTask); // Guardar la tarea actualizada
            System.out.println("CurrentMiningTaskService: Reintentos incrementados para " + currentTask.getBlock().getHash() + " a " + currentTask.getRetries());
        }
    }

    public void clearCurrentTask() {
        redisTemplate.delete(CURRENT_MINING_TASK_KEY);
        System.out.println("CurrentMiningTaskService: Tarea actual eliminada de Redis.");
    }
}