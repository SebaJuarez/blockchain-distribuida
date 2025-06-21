package com.blockchain.miningpool.listeners;

import com.blockchain.miningpool.dtos.MiningTask;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class CandidateBlockListener {

    @RabbitListener(queues = "blocks", containerFactory = "rabbitListenerContainerFactory")
    public void reciveCandidateBlock(MiningTask miningResult) {
        System.out.println("Bloque candidato recibido : " + miningResult);

        // procesar el bloque candidato para dividir en nonces segun la cantidad de gpus que hay
        // y enviar a cada gpu un nonce por rabbit



    }

}
