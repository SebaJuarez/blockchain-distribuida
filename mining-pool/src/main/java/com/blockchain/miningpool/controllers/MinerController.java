package com.blockchain.miningpool.controllers;

import com.blockchain.miningpool.dtos.MiningResult;
import com.blockchain.miningpool.dtos.RegisterResponse;
import com.blockchain.miningpool.models.Miner;
import com.blockchain.miningpool.services.MinerService;
import com.blockchain.miningpool.services.MiningResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pools")
@RequiredArgsConstructor
public class MinerController {

    private final MinerService minerService;
    private final MiningResultService miningResultService;

    @PostMapping("/register")
    public ResponseEntity<EntityModel<RegisterResponse>> registerMiner(@RequestBody Miner miner) {
        if (!miner.isGpuMiner()) {
            RegisterResponse response = new RegisterResponse(HttpStatus.BAD_REQUEST, "Solo se aceptan mineros GPU'S");
            return ResponseEntity.badRequest().body(EntityModel.of(response));
        }
        minerService.addMiner(miner);
        RegisterResponse response = new RegisterResponse(HttpStatus.OK, "El minero se registro exitosamente");
        return ResponseEntity.ok(EntityModel.of(response));
    }

    @PostMapping("/keep-alive")
    public ResponseEntity<EntityModel<RegisterResponse>> keepAlive(@RequestBody Map<String,String> minerPublicKey) {
        if (!minerService.isMinerExists(minerPublicKey.get("minerPublicKey"))) {
            RegisterResponse response = new RegisterResponse(HttpStatus.BAD_REQUEST, "El minero no estaba registrado");
            return ResponseEntity.badRequest().body(EntityModel.of(response));
        }
        minerService.updateKeepAlive(minerPublicKey.get("minerPublicKey"));
        RegisterResponse response = new RegisterResponse(HttpStatus.OK, "Keep-alive actualizado correctamente");
        return ResponseEntity.ok().body(EntityModel.of(response));
    }

    @PostMapping("/{idMiner}/results")
    public ResponseEntity<EntityModel<RegisterResponse>> registerMiningResult(@PathVariable String idMiner, @RequestBody MiningResult miningResult) {
        if(!minerService.isMinerExists(idMiner)){
            return ResponseEntity.badRequest().body(EntityModel.of(new RegisterResponse(HttpStatus.FORBIDDEN, "Minero no perteneciente al pool")));
        }
        boolean isResultValid  = miningResultService.isValidMiningResult(miningResult);
        return isResultValid ?
                ResponseEntity.ok(EntityModel.of(new RegisterResponse(HttpStatus.OK, "Resultado enviado al coordinador.."))) :
                ResponseEntity.badRequest().body(EntityModel.of(new RegisterResponse(HttpStatus.BAD_REQUEST, "Mining result no era valido..")));
    }
}
