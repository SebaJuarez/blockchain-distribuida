package com.blockchain.coordinator.controllers;

import com.blockchain.coordinator.dtos.MiningResult;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.services.BlockService;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @GetMapping("/status")
    public ResponseEntity<EntityModel<String>> getStatus() {
        String statusMessage = "El coordinador está en ejecución ...";
        EntityModel<String> statusModel = EntityModel.of(statusMessage,
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
    public ResponseEntity<?> validateBlock(@RequestBody MiningResult candidateBlock) {
        Optional<Block> added = blockService.addMinedBlock(
                candidateBlock.getBlockId(),
                candidateBlock.getNonce(),
                candidateBlock.getHash()
        );
        if (added.isPresent()) {
            return ResponseEntity.ok("Bloque añadido correctamente.");
        } else {
            return ResponseEntity.badRequest().body("Falló la validación o ya fue resuelto.");
        }
    }

}