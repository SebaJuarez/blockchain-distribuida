package com.blockchain.coordinator.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Transaction implements Serializable {
    private String id;
    private String sender;
    private String receiver;
    private double amount;
    private long timestamp;

    public Transaction(String sender, String receiver, double amount) {
        this.id = UUID.randomUUID().toString();
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }
}