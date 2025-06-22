package com.blockchain.miningpool.dtos;

import com.blockchain.miningpool.models.Transaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MiningResult implements Serializable {
    private String hash;
    private String previous_hash;
    private long nonce;
    private long timestamp;
    private List<Transaction> data;
    private int index;
    private String blockId; // El ID de la tarea original (el hash preliminar del bloque candidato)
    private String minerId; // El ID del minero que encontró la solución
}