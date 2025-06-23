package com.blockchain.miningpool.feingClients;


import com.blockchain.miningpool.dtos.MiningResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "${metadata.service.name:coordinator-node}" , path = "/api/blocks",   url  = "${metadata.coordinator.url:http://localhost:8080}")
public interface CoordinatorClient {

    @PostMapping(
            value = "/result",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<String> sendResult(@RequestBody MiningResult miningTaskResult);
}
