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

@RestController
@RequestMapping("/api/miners")
@RequiredArgsConstructor
public class MinerController {

    private final MinerService minerService;
    private final MiningResultService miningResultService;

    @PostMapping("/register")
    public ResponseEntity<EntityModel<RegisterResponse>> registerMiner(Miner miner) {
        if (!miner.isGpuMiner()) {
            RegisterResponse response = new RegisterResponse(HttpStatus.BAD_REQUEST, "Miner is not a GPU miner");
            return ResponseEntity.badRequest().body(EntityModel.of(response));
        }
        minerService.addMiner(miner);
        RegisterResponse response = new RegisterResponse(HttpStatus.OK, "Miner registered successfully");
        return ResponseEntity.ok(EntityModel.of(response));
    }

    @PostMapping("/keep-alive")
    public ResponseEntity<EntityModel<RegisterResponse>> keepAlive(String minerId) {
        if (!minerService.isMinerExists(minerId)) {
            RegisterResponse response = new RegisterResponse(HttpStatus.BAD_REQUEST, "Miner not registered");
            return ResponseEntity.badRequest().body(EntityModel.of(response));
        }
        minerService.updateKeepAlive(minerId);
        RegisterResponse response = new RegisterResponse(HttpStatus.OK, "Miner keep alive updated successfully");
        return ResponseEntity.ok().body(EntityModel.of(response));
    }

    @PostMapping("/{idMiner}/results")
    public ResponseEntity<EntityModel<RegisterResponse>> registerMiningResult(@PathVariable String idMiner, @RequestBody MiningResult miningResult) {
        if(!minerService.isMinerExists(idMiner)){
            return ResponseEntity.badRequest().body(EntityModel.of(new RegisterResponse(HttpStatus.FORBIDDEN, "Miner not found")));
        }
        boolean isResultValid  = miningResultService.isValidMiningResult(miningResult);
        return isResultValid ?
                ResponseEntity.ok(EntityModel.of(new RegisterResponse(HttpStatus.OK, "Mining result enviado al coordinador.."))) :
                ResponseEntity.badRequest().body(EntityModel.of(new RegisterResponse(HttpStatus.BAD_REQUEST, "Mining result no era valido..")));
    }
}
