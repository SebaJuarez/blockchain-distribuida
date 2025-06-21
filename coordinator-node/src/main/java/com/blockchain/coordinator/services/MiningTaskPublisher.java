package com.blockchain.coordinator.services;

import com.blockchain.coordinator.config.RabbitMQConfig;
import com.blockchain.coordinator.dtos.MiningSolvedStatus;
import com.blockchain.coordinator.dtos.MiningTask;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.ExchangeEvent;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;

@Service
public class MiningTaskPublisher {

    private final AmqpTemplate rabbitTemplate;
    private final MessageConverter jsonMessageConverter;
    private final AmqpAdmin amqpAdmin;

    public MiningTaskPublisher(AmqpTemplate rabbitTemplate, MessageConverter jsonMessageConverter, AmqpAdmin amqpAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMessageConverter = jsonMessageConverter;
        this.amqpAdmin = amqpAdmin;
    }

    // Publica en el exchange Fanout una tarea de minería para un bloque candidato dado.
    public void publishMiningTask(Block blockCandidate, String hashChallenge) {
        if (blockCandidate == null) {
            System.out.println("Error al publicar la tarea, el bloque es nulo.");
            return;
        }

        MiningTask task = new MiningTask();
        task.setChallenge(hashChallenge);
        task.setBlock(blockCandidate);
        task.setEvent(ExchangeEvent.NEW_CANDIDATE_BLOCK);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE.PERSISTENT);

        org.springframework.amqp.core.Message message =
                jsonMessageConverter.toMessage(task, properties);

        rabbitTemplate.send(
                RabbitMQConfig.BLOCKCHAIN_EXCHANGE,
                "",
                message
        );
        System.out.println(
                "Se publicó la tarea de minería para el bloque con index: " + blockCandidate.getIndex() +
                        " (hash ID: " + blockCandidate.getHash() + ") al exchange '" + RabbitMQConfig.BLOCKCHAIN_EXCHANGE + "'."
        );
    }

    public void notifySolvedCandidateBlock(Block solvedBlock, String preliminaryHash, String minerId) {
        if (solvedBlock == null) {
            System.out.println("Error: el bloque resuelto es nulo.");
            return;
        }

        try {
            amqpAdmin.purgeQueue(RabbitMQConfig.BLOCKS_QUEUE, false);
            System.out.println("Se elimino el bloque candidato.");
        } catch (Exception e) {
            System.err.println("Error al eliminar el bloque candidato: " + e.getMessage());
        }

        MiningSolvedStatus solvedTask = new MiningSolvedStatus(
                ExchangeEvent.RESOLVED_CANDIDATE_BLOCK,
                preliminaryHash,
                minerId
        );

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE.PERSISTENT);

        Message message = jsonMessageConverter.toMessage(solvedTask, properties);
        rabbitTemplate.send(RabbitMQConfig.BLOCKCHAIN_EXCHANGE, "", message);
        System.out.println("Se notificó que el bloque: " + preliminaryHash + "  fue resuelto. Evento RESOLVED_CANDIDATE_BLOCK publicado.");
    }
}