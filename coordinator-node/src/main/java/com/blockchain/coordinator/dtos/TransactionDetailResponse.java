package com.blockchain.coordinator.dtos;

import com.blockchain.coordinator.models.Transaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDetailResponse {
    private Transaction transaction;
    private String blockHash;
    private Integer blockIndex;
    private String status; // PENDING | MINED
}
