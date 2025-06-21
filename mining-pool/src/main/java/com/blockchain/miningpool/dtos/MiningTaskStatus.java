package com.blockchain.miningpool.dtos;

import com.blockchain.miningpool.models.ExchangeEvent;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MiningTaskStatus {
    private ExchangeEvent event;
    private String preliminaryHashBlockResolved;
    private String minerId;
}