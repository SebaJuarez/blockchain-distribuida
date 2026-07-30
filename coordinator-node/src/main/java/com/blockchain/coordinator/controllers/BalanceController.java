package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.services.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
@CrossOrigin("*")
public class BalanceController {

    private final BalanceService balanceService;

    @GetMapping("/{publicKey}")
    public Map<String, Object> getBalance(@PathVariable String publicKey) {
        return Map.of("publicKey", publicKey, "balance", balanceService.getBalance(publicKey));
    }
}