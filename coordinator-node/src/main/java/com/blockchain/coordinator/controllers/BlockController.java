package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.dtos.MiningResult;
import com.blockchain.coordinator.dtos.StatusResponse;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.services.BlockService;
import com.blockchain.coordinator.services.CurrentMiningTaskService;
import com.blockchain.coordinator.services.MiningTaskNotifier;
import com.blockchain.coordinator.services.QueueAdminService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
@CrossOrigin("*")
public class BlockController {

    private static final Logger logger = LoggerFactory.getLogger(BlockController.class);
    private final BlockService blockService;
    private final QueueAdminService queueAdminService;
    private final CurrentMiningTaskService currentMiningTaskService;
    private final MiningTaskNotifier miningTaskNotifier;

    @GetMapping("/status")
    public ResponseEntity<EntityModel<StatusResponse>> getStatus() {
        EntityModel<StatusResponse> statusModel = EntityModel.of(new StatusResponse("El coordinador está en ejecución ..."),
                linkTo(methodOn(BlockController.class).getStatus()).withSelfRel());
        return ResponseEntity.ok(statusModel);
    }

    @GetMapping("/latest")
    public ResponseEntity<EntityModel<Block>> getLatestBlock() {
        Block latestBlock = blockService.getLatestBlock();
        if (latestBlock == null) return ResponseEntity.notFound().build();
        EntityModel<Block> blockModel = EntityModel.of(latestBlock,
                linkTo(methodOn(BlockController.class).getLatestBlock()).withSelfRel());
        return ResponseEntity.ok(blockModel);
    }

    @GetMapping("/{blockHash}")
    public ResponseEntity<EntityModel<Block>> getBlockByHash(@PathVariable String blockHash) {
        Optional<Block> blockOptional = blockService.getBlockByHash(blockHash);
        if (blockOptional.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(EntityModel.of(blockOptional.get()));
    }

    @GetMapping
    public ResponseEntity<CollectionModel<EntityModel<Block>>> getAllBlocks() {
        List<EntityModel<Block>> blocks = StreamSupport.stream(blockService.blockRepository.findAll().spliterator(), false)
                .map(EntityModel::of).collect(Collectors.toList());
        return ResponseEntity.ok(CollectionModel.of(blocks));
    }

    @PostMapping("/result")
    @Timed(value = "mining.block.validation.time", description = "Tiempo de CPU validando bloques entrantes")
    public ResponseEntity<String> validateBlock(@RequestBody MiningResult candidateBlock) {
        logger.info("Se recibio el bloque {} del minero {}", candidateBlock.getBlockId(), candidateBlock.getMinerId());
        Optional<Block> addedBlock = blockService.addMinedBlock(candidateBlock.getBlockId(), candidateBlock.getNonce(), candidateBlock.getHash());
        if (addedBlock.isPresent()) {
            Block solvedBlock = addedBlock.get();
            logger.info("Bloque {} añadido exitosamente a la blockchain por el minero {}", solvedBlock.getHash(), candidateBlock.getMinerId());
            blockService.createRewardBlock(candidateBlock.getMinerId());
            queueAdminService.purgeBlocksQueue();
            miningTaskNotifier.notifySolvedCandidateBlock(candidateBlock.getBlockId(), candidateBlock.getMinerId());
            currentMiningTaskService.clearCurrentTask();
            return ResponseEntity.ok("Solución valida, Bloque añadido a la blockchain.");
        } else {
            logger.warn("Fallo la validacion o ya fue resuelto el bloque: {}", candidateBlock.getBlockId());
            return ResponseEntity.badRequest().body("Falló la validación o el bloque ya fue resuelto por otro minero.");
        }
    }
}