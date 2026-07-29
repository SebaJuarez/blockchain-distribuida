package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.models.Transaction;
import com.blockchain.coordinator.services.BlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
@CrossOrigin("*")
public class BalanceController {

    private final BlockService blockService;

    @GetMapping("/{publicKey}")
    public Map<String, Object> getBalance(@PathVariable String publicKey) {
        double balance = StreamSupport.stream(blockService.blockRepository.findAll().spliterator(), false)
                .flatMap(b -> b.getData().stream())
                .filter(tx -> publicKey.equals(tx.getReceiver()))
                .mapToDouble(Transaction::getAmount)
                .sum();
        return Map.of("publicKey", publicKey, "balance", balance);
    }
}