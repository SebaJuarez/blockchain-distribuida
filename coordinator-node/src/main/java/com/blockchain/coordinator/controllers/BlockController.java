package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.dtos.MiningResult;
import com.blockchain.coordinator.dtos.StatusResponse;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
@CrossOrigin("*")
public class BlockController {

    private final BlockService blockService;
    private final QueueAdminService queueAdminService;
    private final CurrentMiningTaskService currentMiningTaskService;
    private final MiningTaskNotifier miningTaskNotifier;

    @GetMapping("/status")
    public ResponseEntity<EntityModel<StatusResponse>> getStatus() {
        String statusMessage = "El coordinador está en ejecución ...";
        EntityModel<StatusResponse> statusModel = EntityModel.of(new StatusResponse(statusMessage),
                linkTo(methodOn(BlockController.class).getStatus()).withSelfRel());
        return ResponseEntity.ok(statusModel);
    }

    @GetMapping("/latest")
    public ResponseEntity<EntityModel<Block>> getLatestBlock() {
        Block latestBlock = blockService.getLatestBlock();
        if (latestBlock == null) {
            return ResponseEntity.notFound().build();
        }

        EntityModel<Block> blockModel = EntityModel.of(latestBlock,
                linkTo(methodOn(BlockController.class).getLatestBlock()).withSelfRel(),
                linkTo(methodOn(BlockController.class).getBlockByHash(latestBlock.getHash())).withRel("self-by-hash"),
                linkTo(methodOn(BlockController.class).getBlockByHash(latestBlock.getPrevious_hash())).withRel("previous-block"),
                linkTo(methodOn(BlockController.class).getAllBlocks()).withRel("all-blocks"));
        return ResponseEntity.ok(blockModel);
    }

    @GetMapping("/{blockHash}")
    public ResponseEntity<EntityModel<Block>> getBlockByHash(@PathVariable String blockHash) {
        Optional<Block> blockOptional = blockService.getBlockByHash(blockHash);

        if (blockOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Block block = blockOptional.get();
        EntityModel<Block> blockModel = EntityModel.of(block,
                linkTo(methodOn(BlockController.class).getBlockByHash(blockHash)).withSelfRel(),
                linkTo(methodOn(BlockController.class).getBlockByHash(block.getPrevious_hash())).withRel("previous-block"),
                linkTo(methodOn(BlockController.class).getLatestBlock()).withRel("latest-block"),
                linkTo(methodOn(BlockController.class).getAllBlocks()).withRel("all-blocks"));
        return ResponseEntity.ok(blockModel);
    }

    @GetMapping
    public ResponseEntity<CollectionModel<EntityModel<Block>>> getAllBlocks() {
        List<EntityModel<Block>> blocks = StreamSupport.stream(blockService.blockRepository.findAll().spliterator(), false)
                .map(block -> EntityModel.of(block,
                        linkTo(methodOn(BlockController.class).getBlockByHash(block.getHash())).withSelfRel(),
                        linkTo(methodOn(BlockController.class).getLatestBlock()).withRel("latest-block")))
                .collect(Collectors.toList());

        return ResponseEntity.ok(CollectionModel.of(blocks,
                linkTo(methodOn(BlockController.class).getAllBlocks()).withSelfRel(),
                linkTo(methodOn(BlockController.class).getLatestBlock()).withRel("latest-block")));
    }

    @PostMapping("/result")
    public ResponseEntity<String> validateBlock(@RequestBody MiningResult candidateBlock) {
        System.out.println("Coordinador: SE RECIBIO EL BLOQUE: " + candidateBlock.getBlockId() + " del minero: " + candidateBlock.getMinerId());
        // valida si es el bloque candidato actual, el hash, la dificultad y la unicidad del previous_hash.
        Optional<Block> addedBlock = blockService.addMinedBlock(
                candidateBlock.getBlockId(),
                candidateBlock.getNonce(),
                candidateBlock.getHash()
        );

        if (addedBlock.isPresent()) {
            Block solvedBlock = addedBlock.get();
            System.out.println("Coordinador: ¡Bloque " + solvedBlock.getHash() + " añadido exitosamente a la blockchain por el minero " + candidateBlock.getMinerId() + "!");
            queueAdminService.purgeBlocksQueue();
            miningTaskNotifier.notifySolvedCandidateBlock(candidateBlock.getBlockId(), candidateBlock.getMinerId());
            currentMiningTaskService.clearCurrentTask();
            return ResponseEntity.ok("Solución valida, Bloque añadido a la blockchain.");
        } else {
            System.out.println("Coordinador: Falló la validación o ya fue resuelto el bloque: " + candidateBlock.getBlockId());
            return ResponseEntity.badRequest().body("Falló la validación o el bloque ya fue resuelto por otro minero.");
        }
    }
}