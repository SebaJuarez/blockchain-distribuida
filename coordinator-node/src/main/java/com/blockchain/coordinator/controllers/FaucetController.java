package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.config.BlockchainConfig;
import com.blockchain.coordinator.dtos.StatusResponse;
import com.blockchain.coordinator.services.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/faucet")
@RequiredArgsConstructor
@CrossOrigin("*")
@ConditionalOnProperty(name = "blockchain.mode", havingValue = "testing")
public class FaucetController {

    private final BalanceService balanceService;
    private final BlockchainConfig config;

    @PostMapping
    public ResponseEntity<?> faucet(@RequestBody Map<String, String> body) {
        String publicKey = body.get("publicKey");
        double amount = Double.parseDouble(body.getOrDefault("amount", "10000"));

        if (publicKey == null || publicKey.isBlank()) {
            return ResponseEntity.badRequest().body(new StatusResponse("publicKey requerido"));
        }

        double systemBalance = balanceService.getSystemBalance();
        if (amount > systemBalance) {
            amount = systemBalance;
        }
        if (amount <= 0) {
            return ResponseEntity.badRequest().body(new StatusResponse("Fondo genesis agotado"));
        }

        balanceService.decrement(config.getSystemAddress(), amount);
        balanceService.increment(publicKey, amount);

        return ResponseEntity.ok(Map.of(
                "publicKey", publicKey,
                "amount", amount,
                "newBalance", balanceService.getBalance(publicKey),
                "systemRemaining", balanceService.getSystemBalance()
        ));
    }

    @GetMapping
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
                "mode", "testing",
                "systemRemaining", balanceService.getSystemBalance(),
                "systemAddress", config.getSystemAddress()
        ));
    }
}