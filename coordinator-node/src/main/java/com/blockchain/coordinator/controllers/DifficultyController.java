package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.services.DifficultyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/difficulty")
@RequiredArgsConstructor
@CrossOrigin("*")
public class DifficultyController {

    private final DifficultyService difficultyService;

    @GetMapping
    public String getDifficulty() {
        return difficultyService.getCurrentChallenge();
    }

    @PostMapping
    public String setDifficulty(@RequestBody Map<String, String> difficulty) {
        difficultyService.setCurrentChallenge(difficulty.get("difficulty"));
        return difficultyService.getCurrentChallenge();
    }

}
