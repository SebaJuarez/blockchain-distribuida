package com.blockchain.coordinator.services;

import com.blockchain.coordinator.config.RabbitMQConfig;
import com.blockchain.coordinator.dtos.MiningTask;
import com.blockchain.coordinator.models.Block;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;

@Service
public class MiningTaskPublisher {

    private final AmqpTemplate rabbitTemplate;
    private final MessageConverter jsonMessageConverter; // Para crear el cuerpo del mensaje JSON

    public MiningTaskPublisher(AmqpTemplate rabbitTemplate, MessageConverter jsonMessageConverter) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMessageConverter = jsonMessageConverter;
    }

    // Publica en la cola una tarea de minería para un bloque candidato dado.
    public void publishMiningTask(Block blockCandidate, String hashChallenge) {
        if (blockCandidate == null) {
            System.out.println("Error al publicar la tarea, el bloque es nulo.");
            return;
        }

        MiningTask task = new MiningTask();
        task.setChallenge(hashChallenge);
        task.setBlock(blockCandidate);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE.PERSISTENT);

        // Convierte el DTO a un mensaje de RabbitMQ
        org.springframework.amqp.core.Message message = jsonMessageConverter.toMessage(task, properties);

        // Envía la tarea de minería a RabbitMQ
        rabbitTemplate.send(RabbitMQConfig.BLOCKCHAIN_EXCHANGE, RabbitMQConfig.BLOCKS_ROUTING_KEY, message);
        System.out.println("Se publico la tarea de mineria para el bloque con index: " + blockCandidate.getIndex() +
                " ( hash ID: " + blockCandidate.getHash() + ") a RabbitMQ.");
    }
}

