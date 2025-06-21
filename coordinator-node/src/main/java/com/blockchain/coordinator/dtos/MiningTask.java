package com.blockchain.coordinator.dtos;

import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.ExchangeEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MiningTask implements Serializable {
    private ExchangeEvent event;
    private String challenge;
    private Block block;
}