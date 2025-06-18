package com.blockchain.coordinator.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RedisHash("Block")
public class Block implements Serializable {
    @Id
    private String hash;
    private String previous_hash;
    private long nonce;
    private long timestamp;
    private List<Transaction> data;
    private int index;

    // Constructor para la creación inicial de un bloque candidato (antes de ser minado).
    public Block(int index, String previous_hash, List<Transaction> data, long timestamp, long nonce, String hash) {
        this.index = index;
        this.previous_hash = previous_hash;
        this.data = data;
        this.timestamp = timestamp;
        this.nonce = nonce;
        this.hash = hash;
    }

    // Constructor simplificado para crear un bloque candidato con valores por defecto
    public Block(int index, String previous_hash, List<Transaction> data) {
        this.index = index;
        this.previous_hash = previous_hash;
        this.data = data;
        this.timestamp = LocalDateTime.now().toEpochSecond(java.time.ZoneOffset.UTC); // Timestamp de creación del candidato
        this.nonce = 0;
        this.hash = "";
    }
}