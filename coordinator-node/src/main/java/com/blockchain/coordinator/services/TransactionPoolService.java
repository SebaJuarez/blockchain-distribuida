package com.blockchain.coordinator.services;

import com.blockchain.coordinator.models.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Service
public class TransactionPoolService {

    // Cola concurrente para almacenar las transacciones pendientes en memoria
    private final Queue<Transaction> pendingTransactions = new ConcurrentLinkedQueue<>();

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public TransactionPoolService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

     // Agrega una nueva transacción al pool de transacciones pendientes en memoria y la almacena en Redis.
    public void addTransaction(Transaction transaction) {
        pendingTransactions.offer(transaction); // Añade al final de la cola en memoria
        System.out.println("Transaction añadida al pool (en memoria): " + transaction.getId());

        // Se almacena la transacción individualmente en Redis (La clave sigue el patrón "transactions:{timestamp}:{sender}")
        String transactionIdKey = "transactions:" + transaction.getTimestamp() + ":" + transaction.getSender();
        try {
            Map<String, Object> transactionMap = objectMapper.convertValue(transaction, new TypeReference<Map<String, Object>>() {});
            Map<String, String> stringMap = transactionMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue() != null ? String.valueOf(e.getValue()) : null
                    ));
            redisTemplate.opsForHash().putAll(transactionIdKey, stringMap);
            System.out.println("Transaccion guardada en redis: " + transactionIdKey);
        } catch (IllegalArgumentException e) {
            System.err.println("Error en la conversion de la transaccion: " + e.getMessage());
            try {
                redisTemplate.opsForValue().set(transactionIdKey, objectMapper.writeValueAsString(transaction));
                System.out.println("Se guarda como string formateada JSON en Redis: " + transactionIdKey);
            } catch (Exception jsonE) {
                System.err.println("Error al guardar la transaccion formateada a JSON string: " + jsonE.getMessage());
            }
        }
    }
    
    // Obtiene un número específico de transacciones pendientes del pool en memoria.
    // Las transacciones se eliminan del pool una vez que se obtienen, para ser incluidas en un bloque.
    public List<Transaction> getPendingTransactions(int count) {
        if (pendingTransactions.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        List<Transaction> transactionsToProcess = new ArrayList<>();
        // Extrae transacciones del pool hasta alcanzar el conteo o el pool esté vacío
        for (int i = 0; i < count && !pendingTransactions.isEmpty(); i++) {
            transactionsToProcess.add(pendingTransactions.poll()); // Elimina y devuelve el elemento principal de la cola
        }
        return transactionsToProcess;
    }

    // Obtiene el número actual de transacciones pendientes en el pool en memoria.
    public int getPendingTransactionCount() {
        return pendingTransactions.size();
    }

    // obtiene una copia de todas las transacciones pendientes sin eliminarlas del pool.
    public List<Transaction> getAllPendingTransactions() {
        return new ArrayList<>(pendingTransactions);
    }
}
