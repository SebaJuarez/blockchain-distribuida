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

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
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

    @PostMapping("/results")
    public ResponseEntity<EntityModel<RegisterResponse>> registerMiningResult(@RequestBody MiningResult miningResult) {
        System.out.println("Tarea del minero: " + miningResult.getMinerId() + " recibida para el bloque: " + miningResult.getBlockId());
        if(!minerService.isMinerExists(miningResult.getMinerId())){
            return ResponseEntity.badRequest().body(EntityModel.of(new RegisterResponse(HttpStatus.FORBIDDEN, "Minero no perteneciente al pool")));
        }
        boolean isResultValid  = miningResultService.isValidMiningResult(miningResult);
        System.out.println(isResultValid);
        return isResultValid ?
                ResponseEntity.ok(EntityModel.of(new RegisterResponse(HttpStatus.OK, "Resultado validado por el coordinador.."))) :
                ResponseEntity.badRequest().body(EntityModel.of(new RegisterResponse(HttpStatus.BAD_REQUEST, "Resultado invalidado por el coordinador..")));
    }
}
