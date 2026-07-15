package com.blockchain.coordinator.services;

import com.blockchain.coordinator.models.Transaction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Service
public class TransactionPoolService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionPoolService.class);
    // Cola concurrente para almacenar las transacciones pendientes en memoria
    private final Queue<Transaction> pendingTransactions = new ConcurrentLinkedQueue<>();
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public TransactionPoolService(RedisTemplate<String, String> redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Gauge.builder("mining.transactions.pending", pendingTransactions, Queue::size)
                .description("Transacciones pendientes en el pool")
                .register(meterRegistry);
    }

     // Agrega una nueva transacción al pool de transacciones pendientes en memoria y la almacena en Redis.
    public void addTransaction(Transaction transaction) {
        pendingTransactions.offer(transaction);
        String transactionIdKey = "transactions:" + transaction.getTimestamp() + ":" + transaction.getSender();
        try {
            Map<String, Object> transactionMap = objectMapper.convertValue(transaction, new TypeReference<Map<String, Object>>() {});
            Map<String, String> stringMap = transactionMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() != null ? String.valueOf(e.getValue()) : null));
            redisTemplate.opsForHash().putAll(transactionIdKey, stringMap);
            logger.debug("Transaccion guardada en redis: {}", transactionIdKey);
        } catch (IllegalArgumentException e) {
            logger.error("Error en la conversion de la transaccion: {}", e.getMessage(), e);
            try {
                redisTemplate.opsForValue().set(transactionIdKey, objectMapper.writeValueAsString(transaction));
            } catch (Exception jsonE) {
            }
        }
    }
    
    // Obtiene un número específico de transacciones pendientes del pool en memoria.
    // Las transacciones se eliminan del pool una vez que se obtienen, para ser incluidas en un bloque.
    public List<Transaction> getPendingTransactions(int count) {
        if (pendingTransactions.isEmpty() || count <= 0) return Collections.emptyList();
        List<Transaction> transactionsToProcess = new ArrayList<>();
        // Extrae transacciones del pool hasta alcanzar el conteo o el pool esté vacío
        for (int i = 0; i < count && !pendingTransactions.isEmpty(); i++) {
            transactionsToProcess.add(pendingTransactions.poll());
        }
        return transactionsToProcess;
    }

    public int getPendingTransactionCount() {
        return pendingTransactions.size();
    }

    public List<Transaction> getAllPendingTransactions() {
        return new ArrayList<>(pendingTransactions);
    }
}