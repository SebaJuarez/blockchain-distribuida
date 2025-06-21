package com.blockchain.coordinator.dtos;

import com.blockchain.coordinator.models.ExchangeEvent;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MiningSolvedStatus {
    private ExchangeEvent event;
    private String preliminaryHashBlockResolved;
    private String minerId;
}
