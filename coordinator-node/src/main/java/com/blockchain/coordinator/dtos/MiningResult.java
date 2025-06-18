package com.blockchain.coordinator.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MiningResult implements Serializable {
    private String hash;
    private String previous_hash;
    private long nonce;
    private long timestamp;
    private Object data; // Object para obtener el JSON de las transacciones
    private int index;
}