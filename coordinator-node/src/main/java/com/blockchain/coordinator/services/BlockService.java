package com.blockchain.coordinator.services;

import com.blockchain.coordinator.dtos.MiningTask;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.Transaction;
import com.blockchain.coordinator.repositories.BlockRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class BlockService {
    private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

    public final BlockRepository blockRepository;
    private final TransactionPoolService transactionPoolService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentMiningTaskService currentMiningTaskService;
    private final DifficultyService difficultyService;
    private final MeterRegistry meterRegistry;
    private final String BLOCK_HASHES_ZSET_KEY = "block_hashes";
    private String latestBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
    private Block latestBlock;

    public BlockService(BlockRepository blockRepository, TransactionPoolService transactionPoolService, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, CurrentMiningTaskService currentMiningTaskService, DifficultyService difficultyService, MeterRegistry meterRegistry) {
        this.blockRepository = blockRepository;
        this.transactionPoolService = transactionPoolService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.currentMiningTaskService = currentMiningTaskService;
        this.difficultyService = difficultyService;
        this.meterRegistry = meterRegistry;

        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    public void init() {
        this.loadLatestBlockFromRedis();
        this.difficultyService.loadCurrentSystemChallenge();
    }

    private void loadLatestBlockFromRedis() {
        Long count = redisTemplate.opsForZSet().size(BLOCK_HASHES_ZSET_KEY);
        if (count == null || count == 0) {
            createGenesisBlock();
        } else {
            Set<String> lastBlockHashes = redisTemplate.opsForZSet().reverseRange(BLOCK_HASHES_ZSET_KEY, 0, 0);
            if (lastBlockHashes != null && !lastBlockHashes.isEmpty()) {
                String lastBlockHashStr = lastBlockHashes.iterator().next();
                Optional<Block> lastKnownBlock = blockRepository.findById(lastBlockHashStr);

                if (lastKnownBlock.isPresent()) {
                    this.latestBlock = lastKnownBlock.get();
                    this.latestBlockHash = latestBlock.getHash();
                    logger.info("BlockService: Se cargó el ultimo bloque desde redis : {}", latestBlockHash);
                } else {
                    logger.error("BlockService: Inconsistency: Latest block hash '{}' found in sorted set, but block object not found in hash store. Recreating Genesis.", lastBlockHashStr);
                    createGenesisBlock();
                }
            } else {
                logger.error("BlockService: Inconsistencia: ZSet size es > 0 pero reverseRange devolvio vacio. Recreando el bloque genesis.");
                createGenesisBlock();
            }
        }
    }

    private void createGenesisBlock() {
        String genesisPreviousHash = "0000000000000000000000000000000000000000000000000000000000000000";
        long genesisTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        List<Transaction> genesisTransactions = Collections.singletonList(new Transaction("system", "genesis", 0.0));
        Block genesisBlock = new Block(0, genesisPreviousHash, genesisTransactions, genesisTimestamp, 0, "");
        genesisBlock.setHash(calculateFinalBlockHash(genesisBlock));
        blockRepository.save(genesisBlock);
        redisTemplate.opsForZSet().add(BLOCK_HASHES_ZSET_KEY, genesisBlock.getHash(), genesisBlock.getTimestamp());
        this.latestBlock = genesisBlock;
        this.latestBlockHash = genesisBlock.getHash();
        logger.info("BlockService: Se creo el bloque genesis: {} (Index: {})", genesisBlock.getHash(), genesisBlock.getIndex());
    }

    public Block createNewMiningCandidateBlock(int numberOfTransactions) {
        List<Transaction> transactions = transactionPoolService.getPendingTransactions(numberOfTransactions);
        if (transactions.isEmpty()) return null;
        Long currentIndexLong = redisTemplate.opsForZSet().size(BLOCK_HASHES_ZSET_KEY);
        int newBlockIndex = (currentIndexLong != null) ? currentIndexLong.intValue() : 0;
        String previousHash = getLatestBlockHash();
        long currentTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        Block newBlock = new Block(newBlockIndex, previousHash, transactions, currentTimestamp, 0, "");
        newBlock.setHash(calculateBlockContentHash(newBlock));
        return newBlock;
    }

    public String calculateBlockContentHash(Block block) {
        try {
            String dataAsString = objectMapper.writeValueAsString(block.getData());
            String contentInput = block.getIndex() + String.valueOf(block.getTimestamp()) + dataAsString + block.getPrevious_hash();
            return applyMd5(contentInput);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error", e);
        }
    }

    public String calculateFinalBlockHash(Block block) {
        return applyMd5(block.getNonce() + calculateBlockContentHash(block));
    }

    public boolean verifyMiningSolution(String blockId, long nonce, String solvedBlockHash) {
        MiningTask currentTask = currentMiningTaskService.getCurrentTask();
        if (currentTask == null || !currentTask.getBlock().getHash().equals(blockId)) return false;
        try {
            Block blockForVerification = currentTask.getBlock().clone();
            blockForVerification.setNonce(nonce);
            return calculateFinalBlockHash(blockForVerification).equals(solvedBlockHash) && solvedBlockHash.startsWith(currentTask.getChallenge());
        } catch (CloneNotSupportedException e) {
            return false;
        }
    }

    public Optional<Block> addMinedBlock(String blockId, long nonce, String solvedBlockHash) {
        MiningTask currentTask = currentMiningTaskService.getCurrentTask();
        if (currentTask == null || !currentTask.getBlock().getHash().equals(blockId) || !verifyMiningSolution(blockId, nonce, solvedBlockHash)) {
            return Optional.empty();
        }

        String previousHashLockKey = "blockchain:" + currentTask.getBlock().getPrevious_hash();
        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(previousHashLockKey, solvedBlockHash, 5, TimeUnit.MINUTES);

        if (acquiredLock == null || !acquiredLock) {
            logger.info("BlockService: Otro bloque fue aceptado por su previousHash: {}. Descartando bloque: {}", currentTask.getBlock().getPrevious_hash(), solvedBlockHash);
            return Optional.empty();
        }

        if (!currentTask.getBlock().getPrevious_hash().equals(this.latestBlockHash)) {
            logger.error("BlockService: El hash previo del bloque minado ({}) no coincide con el bloque actual ({}). Posible bifurcación.", currentTask.getBlock().getPrevious_hash(), this.latestBlockHash);
            redisTemplate.delete(previousHashLockKey);
            return Optional.empty();
        }

        try {
            Block blockToSave = currentTask.getBlock().clone();
            blockToSave.setNonce(nonce);
            blockToSave.setHash(solvedBlockHash);
            blockToSave.setTimestamp(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
            Block savedBlock = blockRepository.save(blockToSave);

            redisTemplate.opsForZSet().add(BLOCK_HASHES_ZSET_KEY, savedBlock.getHash(), savedBlock.getTimestamp());
            this.latestBlock = savedBlock;
            this.latestBlockHash = savedBlock.getHash();

            // MTRICAS
            Timer.builder("mining.block.resolution.time")
                    .tag("difficulty", String.valueOf(currentTask.getChallenge().length()))
                    .register(meterRegistry)
                    .record(System.currentTimeMillis() - currentTask.getCreatedAt(), TimeUnit.MILLISECONDS);

            Counter.builder("mining.blocks.solved")
                    .tag("difficulty", String.valueOf(currentTask.getChallenge().length()))
                    .register(meterRegistry).increment();

            Counter.builder("mining.transactions.processed")
                    .register(meterRegistry).increment(savedBlock.getData().size());

            return Optional.of(savedBlock);
        } catch (CloneNotSupportedException e) {
            logger.error("BlockService: Error al clonar el bloque final para guardar: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public void createRewardBlock(String minerId) {
        long blockTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        List<Transaction> blockTransactions = Collections.singletonList(new Transaction("system", minerId, 20.0));
        Block recompenseBlock = new Block(latestBlock.getIndex() + 1, latestBlockHash, blockTransactions, blockTimestamp, 0, "");
        recompenseBlock.setHash(calculateFinalBlockHash(recompenseBlock));
        blockRepository.save(recompenseBlock);
        redisTemplate.opsForZSet().add(BLOCK_HASHES_ZSET_KEY, recompenseBlock.getHash(), recompenseBlock.getTimestamp());
        this.latestBlock = recompenseBlock;
        this.latestBlockHash = recompenseBlock.getHash();
        logger.info("BlockService: Se creo y añadió el bloque recompensa para el minero: {} Bloque: {} (Index: {})", minerId, recompenseBlock.getHash(), recompenseBlock.getIndex());
    }

    public Optional<Block> getBlockByHash(String blockHash) {
        return blockRepository.findById(blockHash);
    }

    public String getLatestBlockHash() {
        return latestBlockHash;
    }

    public Block getLatestBlock() {
        return latestBlock;
    }

    private String applyMd5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}