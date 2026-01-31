package com.blockchain.coordinator.services;

import com.blockchain.coordinator.config.RabbitMQConfig;
import com.blockchain.coordinator.dtos.MiningTaskStatus;
import com.blockchain.coordinator.dtos.MiningTask;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.ExchangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;

@Service
public class MiningTaskPublisher {

    private static final Logger logger = LoggerFactory.getLogger(MiningTaskPublisher.class);
    
    private final AmqpTemplate rabbitTemplate;
    private MiningTask task;
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
            logger.error("Error al publicar la tarea, el bloque es nulo.");
            return;
        }

        task = new MiningTask();
        task.setChallenge(hashChallenge);
        task.setBlock(blockCandidate);
        task.setRetries(0);
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
        logger.debug(
                "Se publicó la tarea de minería para el bloque con index: " + blockCandidate.getIndex() +
                        " (hash ID: " + blockCandidate.getHash() + ") al exchange '" + RabbitMQConfig.BLOCKCHAIN_EXCHANGE + "'."
        );
    }
  
    public MiningTask getCurrentTask() {
        return task;
    }

    public void incrementRetries() {
        if (task != null) {
            task.setRetries(task.getRetries() + 1);
        }
    }

    public void notifySolvedCandidateBlock(Block solvedBlock, String preliminaryHash, String minerId) {
        if (solvedBlock == null) {
            logger.error("Error: el bloque resuelto es nulo.");
            return;
        }

        try {
            amqpAdmin.purgeQueue(RabbitMQConfig.BLOCKS_QUEUE, false);
            logger.debug("Se elimino el bloque candidato.");
        } catch (Exception e) {
            logger.error("Error al eliminar el bloque candidato: " + e.getMessage(), e);
        }

        MiningTaskStatus solvedTask = new MiningTaskStatus(
                ExchangeEvent.RESOLVED_CANDIDATE_BLOCK,
                preliminaryHash,
                minerId
        );

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE.PERSISTENT);

        Message message = jsonMessageConverter.toMessage(solvedTask, properties);
        rabbitTemplate.send(RabbitMQConfig.BLOCKCHAIN_EXCHANGE, "", message);
        logger.debug("Se notificó que el bloque: " + preliminaryHash + "  fue resuelto. Evento RESOLVED_CANDIDATE_BLOCK publicado.");
    }

    public void notifyMiningTaskDropped(String preliminaryHash)
    {
        MiningTaskStatus solvedTask = new MiningTaskStatus(
                ExchangeEvent.CANDIDATE_BLOCK_DROPPED,
                preliminaryHash,
                ""
        );

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE.PERSISTENT);

        Message message = jsonMessageConverter.toMessage(solvedTask, properties);
        rabbitTemplate.send(RabbitMQConfig.BLOCKCHAIN_EXCHANGE, "", message);

        logger.debug("Se notificó que el bloque: " + preliminaryHash + "  fue dropeado. Evento CANDIDATE_BLOCK_DROPPED publicado.");
    }
}