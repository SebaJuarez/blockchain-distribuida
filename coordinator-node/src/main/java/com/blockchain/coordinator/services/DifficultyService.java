package com.blockchain.coordinator.services;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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

    private volatile String cachedChallenge;

    public DifficultyService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${blockchain.mining.default-hash-challenge}") String defaultHashChallenge,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.defaultHashChallenge = defaultHashChallenge;
        Gauge.builder("mining.difficulty.zeros", this, s -> (double) s.getCurrentChallenge().length())
                .description("Ceros iniciales requeridos (dificultad actual)")
                .register(meterRegistry);
    }

    public void loadCurrentSystemChallenge() {
        String loadedChallenge = redisTemplate.opsForValue().get(CURRENT_SYSTEM_CHALLENGE_KEY);
        if (loadedChallenge != null && !loadedChallenge.isEmpty()) {
            this.cachedChallenge = loadedChallenge;
            logger.info("DifficultyService: Dificultad del sistema cargada desde Redis: {}", cachedChallenge);
        } else {
            this.cachedChallenge = defaultHashChallenge;
            saveCurrentSystemChallenge(this.cachedChallenge);
            logger.info("DifficultyService: No se encontró dificultad del sistema en Redis. Usando por defecto: {}", defaultHashChallenge);
        }
    }

    private void saveCurrentSystemChallenge(String challenge) {
        redisTemplate.opsForValue().set(CURRENT_SYSTEM_CHALLENGE_KEY, challenge);
        logger.info("DifficultyService: Dificultad del sistema guardada en Redis: {}", challenge);
    }

    public void setCurrentChallenge(String newChallenge) {
        this.cachedChallenge = newChallenge;
        saveCurrentSystemChallenge(newChallenge);
        logger.info("DifficultyService: Dificultad del sistema establecida: {}", newChallenge);
    }

    public synchronized void decrementChallenge() {
        String current = getCurrentChallenge();
        if (current.length() > 0) {
            String decremented = current.substring(0, current.length() - 1);
            setCurrentChallenge(decremented);
            logger.info("DifficultyService: Dificultad del sistema decrementada. Nuevo challenge: {}", decremented);
        } else {
            logger.warn("DifficultyService: No se puede decrementar más la dificultad. Ya no hay ceros en el challenge.");
        }
    }

    // Lee siempre de Redis primero.
    // Si Redis no responde, cae al último valor conocido localmente.
    public String getCurrentChallenge() {
        try {
            String fromRedis = redisTemplate.opsForValue().get(CURRENT_SYSTEM_CHALLENGE_KEY);
            if (fromRedis != null && !fromRedis.isEmpty()) {
                this.cachedChallenge = fromRedis;
                return fromRedis;
            }
        } catch (Exception e) {
            logger.warn("DifficultyService: no se pudo leer la dificultad desde Redis, usando cache local: {}", e.getMessage());
        }
        return this.cachedChallenge != null ? this.cachedChallenge : defaultHashChallenge;
    }
}