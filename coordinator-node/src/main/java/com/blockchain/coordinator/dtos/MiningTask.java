package com.blockchain.coordinator.dtos;

import com.blockchain.coordinator.models.Block;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MiningTask implements Serializable {
    private String challenge;
    private Block block;
}