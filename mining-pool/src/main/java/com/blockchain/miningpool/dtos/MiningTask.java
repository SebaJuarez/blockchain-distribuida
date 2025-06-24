package com.blockchain.miningpool.dtos;

import com.blockchain.miningpool.models.Block;
import com.blockchain.miningpool.models.ExchangeEvent;
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
    private Integer retries;
}