package com.blockchain.coordinator.services;

import com.blockchain.coordinator.dtos.MiningTask;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.Transaction;
import com.blockchain.coordinator.repositories.BlockRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    public final BlockRepository blockRepository;
    private final TransactionPoolService transactionPoolService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentMiningTaskService currentMiningTaskService;
    private final DifficultyService difficultyService;
    private final String BLOCK_HASHES_ZSET_KEY = "block_hashes";
    private String latestBlockHash = "0000000000000000000000000000000000000000000000000000000000000000";
    private Block latestBlock;

    public BlockService(BlockRepository blockRepository, TransactionPoolService transactionPoolService, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, CurrentMiningTaskService currentMiningTaskService, DifficultyService difficultyService) {
        this.blockRepository = blockRepository;
        this.transactionPoolService = transactionPoolService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.currentMiningTaskService = currentMiningTaskService;
        this.difficultyService = difficultyService;

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
                    System.out.println("BlockService: Se cargó el ultimo bloque desde redis : " + latestBlockHash);
                } else {
                    System.err.println("BlockService: Inconsistency: Latest block hash '" + lastBlockHashStr + "' found in sorted set, but block object not found in hash store. Recreating Genesis.");
                    createGenesisBlock();
                }
            } else {
                System.err.println("BlockService: Inconsistencia: ZSet size es > 0 pero reverseRange devolvio vacio. Recreando el bloque genesis.");
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
        System.out.println("BlockService: Se creo el bloque genesis: " + genesisBlock.getHash() + " (Index: " + genesisBlock.getIndex() + ")");
    }

    public Block createNewMiningCandidateBlock(int numberOfTransactions) {
        List<Transaction> transactions = transactionPoolService.getPendingTransactions(numberOfTransactions);
        if (transactions.isEmpty()) {
            System.out.println("BlockService: No hay transacciones pendientes para crear el bloque.");
            return null;
        }

        Long currentIndexLong = redisTemplate.opsForZSet().size(BLOCK_HASHES_ZSET_KEY);
        int newBlockIndex = (currentIndexLong != null) ? currentIndexLong.intValue() : 0;

        String previousHash = getLatestBlockHash();
        long currentTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

        Block newBlock = new Block(newBlockIndex, previousHash, transactions, currentTimestamp, 0, "");
        String preliminaryHash = calculateBlockContentHash(newBlock);
        newBlock.setHash(preliminaryHash);

        System.out.println("BlockService: Se creó el bloque candidato con el id (hash): " + preliminaryHash +
                " para el bloque previo: " + previousHash + " (Index: " + newBlockIndex + ")");
        return newBlock;
    }

    public String calculateBlockContentHash(Block block) {
        String dataAsString = "";
        try {
            dataAsString = objectMapper.writeValueAsString(block.getData());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error al serializar el bloque.", e);
        }

        String contentInput = String.valueOf(block.getIndex()) +
                String.valueOf(block.getTimestamp()) +
                dataAsString +
                block.getPrevious_hash();

        return applyMd5(contentInput);
    }

    public String calculateFinalBlockHash(Block block) {
        String blockContentHash = calculateBlockContentHash(block);
        String finalHashInput = String.valueOf(block.getNonce()) + blockContentHash;
        return applyMd5(finalHashInput);
    }

    public boolean verifyMiningSolution(String blockId, long nonce, String solvedBlockHash) {
        MiningTask currentTask = currentMiningTaskService.getCurrentTask();
        if (currentTask == null || !currentTask.getBlock().getHash().equals(blockId)) {
            System.out.println("BlockService: No se encontró el bloque candidato activo con id: " + blockId + " o no coincide con la tarea actual. Puede que haya expirado o ya se procesó.");
            return false;
        }

        String challengeForThisTask = currentTask.getChallenge();
        Block blockCandidate = currentTask.getBlock();

        Block blockForVerification;
        try {
            blockForVerification = (Block) blockCandidate.clone();
            blockForVerification.setNonce(nonce);
        } catch (CloneNotSupportedException e) {
            System.err.println("BlockService: Error al clonar el bloque para verificación: " + e.getMessage());
            return false;
        }

        String calculatedHash = calculateFinalBlockHash(blockForVerification);

        boolean hashMatches = calculatedHash.equals(solvedBlockHash);
        boolean difficultyMet = solvedBlockHash.startsWith(challengeForThisTask);

        if (!hashMatches) {
            System.out.printf(
                    "BlockService: Fallo la verificación del bloque %s: hash calculado=%s, hash entregado=%s%n",
                    blockId, calculatedHash, solvedBlockHash
            );
        }
        if (!difficultyMet) {
            System.out.printf(
                    "BlockService: Fallo la dificultad para el bloque %s: hash entregado=%s, prefijo requerido=%s%n",
                    blockId, solvedBlockHash, challengeForThisTask
            );
        }

        return hashMatches && difficultyMet;
    }

    public Optional<Block> addMinedBlock(String blockId, long nonce, String solvedBlockHash) {
        MiningTask currentTask = currentMiningTaskService.getCurrentTask();
        if (currentTask == null || !currentTask.getBlock().getHash().equals(blockId)) {
            System.out.println("BlockService: No se encontró el bloque candidato activo con id: " + blockId + " o no coincide con la tarea actual. Puede que haya expirado o ya se procesó.");
            return Optional.empty();
        }
        Block verifiedBlockCandidate = currentTask.getBlock();

        if (!verifyMiningSolution(blockId, nonce, solvedBlockHash)) {
            System.out.println("BlockService: Error al añadir el bloque: falló la verificación para el id:  " + blockId);
            return Optional.empty();
        }

        String previousHashLockKey = "blockchain:" + verifiedBlockCandidate.getPrevious_hash();
        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(previousHashLockKey, solvedBlockHash, 5, TimeUnit.MINUTES);

        if (acquiredLock == null || !acquiredLock) {
            System.out.println("BlockService: Otro bloque fue aceptado por su previousHash: " + verifiedBlockCandidate.getPrevious_hash() + ". Descartando bloque: " + solvedBlockHash);
            return Optional.empty();
        }

        if (!verifiedBlockCandidate.getPrevious_hash().equals(this.latestBlockHash)) {
            System.out.println("BlockService: El hash previo del bloque minado (" + verifiedBlockCandidate.getPrevious_hash() + ") no coincide con el bloque actual (" + this.latestBlockHash + "). Posible bifurcación.");
            redisTemplate.delete(previousHashLockKey);
            return Optional.empty();
        }

        Block blockToSave;
        try {
            blockToSave = (Block) verifiedBlockCandidate.clone();
            blockToSave.setNonce(nonce);
            blockToSave.setHash(solvedBlockHash);
            blockToSave.setTimestamp(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        } catch (CloneNotSupportedException e) {
            System.err.println("BlockService: Error al clonar el bloque final para guardar: " + e.getMessage());
            return Optional.empty();
        }

        Block savedBlock = blockRepository.save(blockToSave);

        redisTemplate.opsForZSet().add(BLOCK_HASHES_ZSET_KEY, savedBlock.getHash(), savedBlock.getTimestamp());
        this.latestBlock = savedBlock;
        this.latestBlockHash = savedBlock.getHash();

        System.out.println("BlockService: Se añadió correctamente el bloque a la blockchain: " + savedBlock.getHash() + " (Nonce: " + savedBlock.getNonce() + ", Index: " + savedBlock.getIndex() + ")");
        return Optional.of(savedBlock);
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
        System.out.println("BlockService: Se creo y añadió el bloque recompensa para el minero: " + minerId + " Bloque: " + recompenseBlock.getHash() + " (Index: " + recompenseBlock.getIndex() + ")");
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

    public void decrementHashChallenge() {
        difficultyService.decrementChallenge();
    }

    public String getHashChallenge() {
        return difficultyService.getCurrentChallenge();
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
            throw new RuntimeException("MD5 no disponible.", e);
        }
    }
}