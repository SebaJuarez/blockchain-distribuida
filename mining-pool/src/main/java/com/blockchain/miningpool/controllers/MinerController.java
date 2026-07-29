package com.blockchain.miningpool.controllers;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.dtos.RegisterResponse;
import com.blockchain.miningpool.dtos.StatusResponse;
import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.services.MinerService;
import com.blockchain.miningpool.services.MiningResultService;
import jakarta.servlet.http.HttpServletRequest;
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
@CrossOrigin("*")
public class MinerController {
        
        private final MinerService minerService;
        private final MiningResultService miningResultService;
        private final com.blockchain.miningpool.config.PoolKeyConfig poolKeyConfig;
        private final com.blockchain.miningpool.services.PoolAccountingService poolAccountingService;
        
        @PostMapping("/register")
        public ResponseEntity<EntityModel<RegisterResponse>> registerMiner(@RequestBody Miner miner) {
                if (!miner.isGpuMiner()) {
                        RegisterResponse resp = new RegisterResponse(HttpStatus.BAD_REQUEST,
                                        "Solo se aceptan mineros GPU");
                        return ResponseEntity
                                        .badRequest()
                                        .body(EntityModel.of(resp));
                }
                boolean added = minerService.addMiner(miner);
                if (!added) {
                        RegisterResponse resp = new RegisterResponse(HttpStatus.BAD_REQUEST,
                                        "Clave pública inválida: debe ser un punto EC secp256k1 en hex (comprimido 33 bytes o descomprimido 65 bytes).");
                        return ResponseEntity.badRequest().body(EntityModel.of(resp));
                }
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

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPoolPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", poolKeyConfig.getPublicKeyHex()));
    }

    @GetMapping("/miners/{publicKey}/balance")
    public ResponseEntity<Map<String, Object>> getMinerBalance(@PathVariable String publicKey) {
        double balance = poolAccountingService.getBalance(publicKey);
        return ResponseEntity.ok(Map.of("publicKey", publicKey, "balance", balance));
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
    public ResponseEntity<EntityModel<RegisterResponse>> registerMiningResult(@RequestBody MiningResult result, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        System.out.println(clientIp+" Envio un resultado");
        boolean minerExists = minerService.isMinerExists(result.getMinerId());
        boolean ipAllowed  = clientIp.startsWith("10.") || clientIp.startsWith("3");

        if (!minerExists && !ipAllowed) {
            RegisterResponse resp = new RegisterResponse(
                    HttpStatus.FORBIDDEN,
                    "Minero no pertenece al pool");
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