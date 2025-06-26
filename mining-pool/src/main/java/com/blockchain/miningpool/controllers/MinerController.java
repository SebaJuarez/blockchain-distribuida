package com.blockchain.miningpool.controllers;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.dtos.RegisterResponse;
import com.blockchain.miningpool.dtos.StatusResponse;
import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.services.MinerService;
import com.blockchain.miningpool.services.MiningResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/pools")
@RequiredArgsConstructor
public class MinerController {

    private final MinerService minerService;
    private final MiningResultService miningResultService;

    @PostMapping("/register")
    public ResponseEntity<EntityModel<RegisterResponse>> registerMiner(@RequestBody Miner miner) {
        if (!miner.isGpuMiner()) {
            RegisterResponse resp = new RegisterResponse(HttpStatus.BAD_REQUEST, "Solo se aceptan mineros GPU");
            return ResponseEntity
                    .badRequest()
                    .body(EntityModel.of(resp));
        }
        minerService.addMiner(miner);
        RegisterResponse resp = new RegisterResponse(HttpStatus.OK, "Minero registrado exitosamente");
        return ResponseEntity
                .ok(EntityModel.of(resp));
    }

    @GetMapping("/miners")
    public ResponseEntity<CollectionModel<EntityModel<Miner>>> getMinersCount() {
        List<EntityModel<Miner>> miners = minerService.getMiners().stream()
                .map(miner -> EntityModel.of(
                        miner,
                        linkTo(methodOn(MinerController.class).getById(miner.getPublicKey())).withSelfRel()
                ))
                .collect(Collectors.toList());

        CollectionModel<EntityModel<Miner>> collection = CollectionModel.of(
                miners,
                linkTo(methodOn(MinerController.class).getMinersCount()).withSelfRel()
        );

        return ResponseEntity.ok(collection);
    }

    @GetMapping("/miners/{id}")
    public ResponseEntity<EntityModel<?>> getById(@PathVariable String id) {
        Optional<Miner> minerOpt = minerService.findById(id);

        if (minerOpt.isPresent()) {
            Miner miner = minerOpt.get();
            EntityModel<Miner> resource = EntityModel.of(
                    miner,
                    linkTo(methodOn(MinerController.class).getById(id)).withSelfRel(),
                    linkTo(methodOn(MinerController.class).getMinersCount()).withRel("miners")
            );
            return ResponseEntity.ok(resource);
        }

        // Si no existe, devolvemos un StatusResponse con 404
        StatusResponse errorBody = new StatusResponse("Miner no encontrado con id: " + id);
        EntityModel<StatusResponse> errorResource = EntityModel.of(
                errorBody,
                linkTo(methodOn(MinerController.class).getMinersCount()).withRel("miners")
        );
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResource);
    }


    @PostMapping("/keep-alive")
    public ResponseEntity<EntityModel<RegisterResponse>> keepAlive(@RequestBody Map<String, String> body) {
        String key = body.get("minerPublicKey");
        if (!minerService.isMinerExists(key)) {
            RegisterResponse resp = new RegisterResponse(HttpStatus.BAD_REQUEST, "Minero no registrado");
            return ResponseEntity
                    .badRequest()
                    .body(EntityModel.of(resp));
        }
        minerService.updateKeepAlive(key);
        RegisterResponse resp = new RegisterResponse(HttpStatus.OK, "Keep-alive actualizado");
        return ResponseEntity
                .ok(EntityModel.of(resp));
    }

    @PostMapping("/results")
    public ResponseEntity<EntityModel<RegisterResponse>> registerMiningResult(@RequestBody MiningResult result) {
        if (!minerService.isMinerExists(result.getMinerId())) {
            RegisterResponse resp = new RegisterResponse(HttpStatus.FORBIDDEN, "Minero no pertenece al pool");
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(EntityModel.of(resp));
        }
        boolean valid = miningResultService.isValidMiningResult(result);
        RegisterResponse resp = valid
                ? new RegisterResponse(HttpStatus.OK, "Resultado validado correctamente")
                : new RegisterResponse(HttpStatus.BAD_REQUEST, "Resultado invalidado");

        return valid
                ? ResponseEntity.ok(EntityModel.of(resp))
                : ResponseEntity.badRequest().body(EntityModel.of(resp));
    }
}