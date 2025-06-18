package com.blockchain.coordinator.services;

import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.Transaction;
import com.blockchain.coordinator.repositories.BlockRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class BlockService {

    public final BlockRepository blockRepository;
    private final TransactionPoolService transactionPoolService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Clave para el sorted set en Redis que almacena los hashes de los bloques en orden
    private final String BLOCK_HASHES_ZSET_KEY = "block_hashes";

    @Value("${blockchain.mining.hash-challenge}")
    private String hashChallenge; // El prefijo de ceros que debe tener el hash

    // El hash del último bloque confirmado en la cadena
    private String latestBlockHash = "0000000000000000000000000000000000000000000000000000000000000000"; // Hash inicial para el bloque Génesis
    // Referencia al último bloque confirmado en memoria para acceso rápido
    private Block latestBlock;

    // Mapa para almacenar bloques que están actualmente en proceso de minería (candidatos).
    // La clave es el hash anterior del bloque (ID de la tarea de minería).
    private final ConcurrentHashMap<String, Block> blocksInProgress = new ConcurrentHashMap<>();

    public BlockService(BlockRepository blockRepository, TransactionPoolService transactionPoolService, RedisTemplate<String, String> redisTemplate) {
        this.blockRepository = blockRepository;
        this.transactionPoolService = transactionPoolService;
        this.redisTemplate = redisTemplate;

        // ObjectMapper para serialización consistente de transacciones para hashing
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // carga el ultimo bloque o crea el genesis
    public void init() {
        this.loadLatestBlockFromRedis();
    }

    // Carga el último bloque conocido desde Redis al iniciar el servicio.
    // Si no encuentra bloques, crea y persiste un Bloque Génesis.
    private void loadLatestBlockFromRedis() {
        Long count = redisTemplate.opsForZSet().size(BLOCK_HASHES_ZSET_KEY); // Equivalente a zcount

        if (count == null || count == 0) {
            createGenesisBlock();
        } else {
            // Recuperar el hash del último bloque del sorted set
            Set<String> lastBlockHashes = redisTemplate.opsForZSet().reverseRange(BLOCK_HASHES_ZSET_KEY, 0, 0);
            if (lastBlockHashes != null && !lastBlockHashes.isEmpty()) {
                String lastBlockHashStr = lastBlockHashes.iterator().next();
                Optional<Block> lastKnownBlock = blockRepository.findById(lastBlockHashStr); // Recuperar el Block completo por su hash

                if (lastKnownBlock.isPresent()) {
                    this.latestBlock = lastKnownBlock.get();
                    this.latestBlockHash = latestBlock.getHash();
                    System.out.println("Se cargó el ultimo bloque desde redis : " + latestBlockHash);
                } else {
                    System.err.println("Inconsistency: Latest block hash '" + lastBlockHashStr + "' found in sorted set, but block object not found in hash store. Recreating Genesis.");
                    createGenesisBlock(); // Fallback en caso de inconsistencia
                }
            } else {
                // por las dudas que haya alguna inconsistencia
                System.err.println("Inconsistencia: ZSet size es > 0 pero reverseRange devolvio vacio. Recreando el bloque genesis.");
                createGenesisBlock();
            }
        }
    }

    private void createGenesisBlock() {
        String genesisPreviousHash = "0000000000000000000000000000000000000000000000000000000000000000";
        long genesisTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC); // Timestamp en segundos
        List<Transaction> genesisTransactions = List.of(new Transaction("system", "genesis", 0.0));

        Block genesisBlock = new Block(0, genesisPreviousHash, genesisTransactions, genesisTimestamp, 0, "");
        genesisBlock.setHash(calculateFinalBlockHash(genesisBlock));

        blockRepository.save(genesisBlock); // Persistir el Génesis en Redis (tipo HASH)
        // Añadir el hash del Génesis al sorted set 'block_hashes'
        redisTemplate.opsForZSet().add(BLOCK_HASHES_ZSET_KEY, genesisBlock.getHash(), genesisBlock.getTimestamp());
        this.latestBlock = genesisBlock;
        this.latestBlockHash = genesisBlock.getHash();
        System.out.println("Se creo el bloque genesis: " + genesisBlock.getHash() + " (Index: " + genesisBlock.getIndex() + ")");
    }

    // se crea un nuevo bloque candidato para la minería
    // este bloque contiene transacciones pendientes y la referencia al último bloque.
    public Block createNewMiningCandidateBlock(int numberOfTransactions) {
        List<Transaction> transactions = transactionPoolService.getPendingTransactions(numberOfTransactions);
        if (transactions.isEmpty()) {
            System.out.println("No hay transacciones pendientes para crear el bloque.");
            return null;
        }

        // Obtener el índice del nuevo bloque (el conteo actual de bloques en el sorted set)
        Long currentIndexLong = redisTemplate.opsForZSet().size(BLOCK_HASHES_ZSET_KEY);
        int newBlockIndex = (currentIndexLong != null) ? currentIndexLong.intValue() : 0;

        String previousHash = getLatestBlockHash(); // Hash del último bloque confirmado
        long currentTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC); // Timestamp de la creación del candidato

        // Se crea el candidato. Su 'hash' y 'nonce' son valores por defecto para ser resueltos.
        Block newBlock = new Block(newBlockIndex, previousHash, transactions, currentTimestamp, 0, "");
        // se calcula el hash (solo contenido, sin nonce) para la tarea
        String preliminaryHash = calculateBlockContentHash(newBlock);
        newBlock.setHash(preliminaryHash); // se usa el hash para identificar el bloque

        blocksInProgress.put(preliminaryHash, newBlock); // Almacenar el candidato para futura verificación

        System.out.println("Se creo el bloque candidato con el id (hash): " + preliminaryHash +
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
        // Primero, calcular el hash del contenido
        String blockContentHash = calculateBlockContentHash(block);
        // Luego, calcular el hash final combinando el nonce y el hash del contenido
        String finalHashInput = String.valueOf(block.getNonce()) + blockContentHash;
        return applyMd5(finalHashInput);
    }

    public boolean verifyMiningSolution(String blockId, long nonce, String solvedBlockHash) {
        Block blockCandidate = blocksInProgress.get(blockId);
        if (blockCandidate == null) {
            System.out.println("No se encontro el bloque candidato con el di: " + blockId + ". Pude que fue resuelto o expiro.");
            return false;
        }

        // bloque para calcular el hash con el nonce propuesto sin modificar el original.
        Block tempBlock = new Block(
                blockCandidate.getIndex(),
                blockCandidate.getPrevious_hash(),
                blockCandidate.getData(),
                blockCandidate.getTimestamp(),
                nonce,
                solvedBlockHash
        );

        String calculatedHash = calculateFinalBlockHash(tempBlock); // se calcula el hash final con el nonce

        boolean hashMatches = calculatedHash.equals(solvedBlockHash);
        // Comprueba si el hash comienza con el prefijo de dificultad (hashChallenge)
        boolean difficultyMet = solvedBlockHash.startsWith(hashChallenge);

        if (!hashMatches) {
            System.out.println("Fallo la verificacion del bloque:  " + blockId + ": El hash no concuerda. Se calculo: " + calculatedHash + ", Se entrego: " + solvedBlockHash);
        }
        if (!difficultyMet) {
            System.out.println("Fallo la verificacion del bloque:  " + blockId + ": La dificultad no concuerda. Hash entregado: " + solvedBlockHash + ", Dificultad esperada: " + hashChallenge);
        }

        return hashMatches && difficultyMet;
    }

    public Optional<Block> addMinedBlock(String blockId, long nonce, String solvedBlockHash) {
        // Verificamos que la solución es válida
        if (!verifyMiningSolution(blockId, nonce, solvedBlockHash)) {
            System.out.println("Error al añadir el bloque: fallo la verificación para el id:  " + blockId);
            blocksInProgress.remove(blockId); // Se descarta el candidato si la verificación falla
            return Optional.empty();
        }

        Block verifiedBlockCandidate = blocksInProgress.get(blockId);

        // Intenta adquirir un "bloqueo" en Redis para este previous_hash
        // La clave es "blockchain:{previous_hash}" y el valor es el hash del bloque que ganó.
        String previousHashLockKey = "blockchain:" + verifiedBlockCandidate.getPrevious_hash();
        Boolean acquiredLock = redisTemplate.opsForValue().setIfAbsent(previousHashLockKey, solvedBlockHash, 5, TimeUnit.MINUTES);

        if (acquiredLock == null || !acquiredLock) { // Si el bloque ya existía, otro minero ganó
                System.out.println("Otro bloque fue aceptado por su previousHash: " + verifiedBlockCandidate.getPrevious_hash() + ". Descartando block: " + solvedBlockHash);
            blocksInProgress.remove(blockId); // No se procesa, ya hay un ganador para esta tarea
            return Optional.empty();
        }

        // verifica que el `previous_hash` del bloque minado coincida con el `latestBlockHash`
        // Esto previene que se agreguen bloques "stale" o de bifurcaciones no deseadas.
        if (!verifiedBlockCandidate.getPrevious_hash().equals(this.latestBlockHash)) {
            System.out.println("El hash previo del bloque minado (" + verifiedBlockCandidate.getPrevious_hash() + ") no coincide con el bloque actual (" + this.latestBlockHash + "). Posible bifurcación.");
            blocksInProgress.remove(blockId);
            redisTemplate.delete(previousHashLockKey); // Liberar el lock si no es parte de la cadena principal
            return Optional.empty();
        }

        // Completamos el bloque con los datos resueltos por el minero
        verifiedBlockCandidate.setNonce(nonce);
        verifiedBlockCandidate.setHash(solvedBlockHash); // Establece el hash final del bloque
        verifiedBlockCandidate.setTimestamp(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)); // Actualiza timestamp a la hora de confirmación

        // persistimos el bloque completo y ya validado en Redis
        Block savedBlock = blockRepository.save(verifiedBlockCandidate);

        // Añadimos el hash del bloque al sorted set 'block_hashes' para mantener la cadena ordenada
        redisTemplate.opsForZSet().add(BLOCK_HASHES_ZSET_KEY, savedBlock.getHash(), savedBlock.getTimestamp());

        // Actualizamos la ref al ultimo bloque
        this.latestBlock = savedBlock;
        this.latestBlockHash = savedBlock.getHash();

        // Se elimina el bloque de candidatos porque ya se resolvio
        blocksInProgress.remove(blockId);

        System.out.println("Se añadio correctamente el bloque a la blockchain: " + savedBlock.getHash() + " (Nonce: " + savedBlock.getNonce() + ", Index: " + savedBlock.getIndex() + ")");
        return Optional.of(savedBlock);
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
            throw new RuntimeException("MD5 no disponible.", e);
        }
    }
}