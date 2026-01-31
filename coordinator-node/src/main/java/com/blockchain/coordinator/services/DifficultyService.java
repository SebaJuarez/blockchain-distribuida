package com.blockchain.coordinator.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DifficultyService {

    private static final Logger logger = LoggerFactory.getLogger(DifficultyService.class);
    
    private final RedisTemplate<String, String> redisTemplate;

    private static final String CURRENT_SYSTEM_CHALLENGE_KEY = "current_system_challenge";
    private final String defaultHashChallenge;

    private String currentSystemChallenge;

    public DifficultyService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${blockchain.mining.default-hash-challenge}") String defaultHashChallenge) {
        this.redisTemplate = redisTemplate;
        this.defaultHashChallenge = defaultHashChallenge;
    }

    public void loadCurrentSystemChallenge() {
        String loadedChallenge = redisTemplate.opsForValue().get(CURRENT_SYSTEM_CHALLENGE_KEY);
        if (loadedChallenge != null && !loadedChallenge.isEmpty()) {
            this.currentSystemChallenge = loadedChallenge;
            logger.info("DifficultyService: Dificultad del sistema cargada desde Redis: " + currentSystemChallenge);
        } else {
            this.currentSystemChallenge = defaultHashChallenge;
            saveCurrentSystemChallenge();
            logger.info("DifficultyService: No se encontró dificultad del sistema en Redis. Usando por defecto: " + defaultHashChallenge);
        }
    }

    private void saveCurrentSystemChallenge() {
        redisTemplate.opsForValue().set(CURRENT_SYSTEM_CHALLENGE_KEY, this.currentSystemChallenge);
        logger.info("DifficultyService: Dificultad del sistema guardada en Redis: " + this.currentSystemChallenge);
    }

    public void setCurrentChallenge(String newChallenge) {
        this.currentSystemChallenge = newChallenge;
        saveCurrentSystemChallenge();
        logger.info("DifficultyService: Dificultad del sistema establecida: " + newChallenge);
    }

    public void decrementChallenge() {
        if (currentSystemChallenge.length() > 0) {
            this.currentSystemChallenge = currentSystemChallenge.substring(0, currentSystemChallenge.length() - 1);
            saveCurrentSystemChallenge();
            logger.info("DifficultyService: Dificultad del sistema decrementada. Nuevo challenge: " + currentSystemChallenge);
        } else {
            logger.warn("DifficultyService: No se puede decrementar más la dificultad. Ya no hay ceros en el challenge.");
        }
    }

    public String getCurrentChallenge() {
        return this.currentSystemChallenge;
    }
}