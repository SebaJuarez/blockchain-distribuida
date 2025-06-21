package com.blockchain.miningpool.dtos;

import com.blockchain.miningpool.models.Block;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubTask {
    private Block block;
    private String challenge;
    private long from;
    private long to;
}