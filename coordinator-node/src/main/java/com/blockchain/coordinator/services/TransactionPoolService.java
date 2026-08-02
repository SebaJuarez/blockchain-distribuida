package com.blockchain.coordinator.services;

import com.blockchain.coordinator.models.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TransactionPoolService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionPoolService.class);
    private static final String PENDING_TX_LIST_KEY = "pending_transactions";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public TransactionPoolService(RedisTemplate<String, String> redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Gauge.builder("mining.transactions.pending", this, TransactionPoolService::getPendingTransactionCount)
                .description("Transacciones pendientes en el pool")
                .register(meterRegistry);
    }

    // Agrega una nueva transacción a la lista de pendientes en Redis
    public void addTransaction(Transaction transaction) {
        try {
            redisTemplate.opsForList().rightPush(PENDING_TX_LIST_KEY, objectMapper.writeValueAsString(transaction));
        } catch (JsonProcessingException e) {
            logger.error("Error serializando la transacción para la cola de pendientes: {}", e.getMessage(), e);
            return;
        }

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
                // best-effort, no rompe el flujo principal
            }
        }
    }

    // Obtiene y remueve hasta 'count' transacciones pendientes de Redis.
    public List<Transaction> getPendingTransactions(int count) {
        if (count <= 0) return Collections.emptyList();
        List<Transaction> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String raw = redisTemplate.opsForList().leftPop(PENDING_TX_LIST_KEY);
            if (raw == null) break; // la lista quedó vacía
            Transaction tx = deserialize(raw);
            if (tx != null) result.add(tx);
        }
        return result;
    }

    public int getPendingTransactionCount() {
        Long size = redisTemplate.opsForList().size(PENDING_TX_LIST_KEY);
        return size != null ? size.intValue() : 0;
    }

    public List<Transaction> getAllPendingTransactions() {
        List<String> raw = redisTemplate.opsForList().range(PENDING_TX_LIST_KEY, 0, -1);
        if (raw == null) return Collections.emptyList();
        List<Transaction> result = new ArrayList<>();
        for (String s : raw) {
            Transaction tx = deserialize(s);
            if (tx != null) result.add(tx);
        }
        return result;
    }

    private Transaction deserialize(String raw) {
        try {
            return objectMapper.readValue(raw, Transaction.class);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializando transacción pendiente desde Redis: {}", e.getMessage(), e);
            return null;
        }
    }
}