package com.blockchain.coordinator.services;

import com.blockchain.coordinator.config.RabbitMQConfig;
import com.blockchain.coordinator.dtos.MiningTaskStatus;
import com.blockchain.coordinator.dtos.MiningTask;
import com.blockchain.coordinator.models.Block;
import com.blockchain.coordinator.models.ExchangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;

@Service
public class MiningTaskNotifier {

    private static final Logger logger = LoggerFactory.getLogger(MiningTaskNotifier.class);
    
    private final AmqpTemplate rabbitTemplate;
    private final MessageConverter jsonMessageConverter;

    public MiningTaskNotifier(AmqpTemplate rabbitTemplate, MessageConverter jsonMessageConverter) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMessageConverter = jsonMessageConverter;
    }

    public void notifyNewMiningTask(Block blockCandidate, String hashChallenge, int retries) {
        if (blockCandidate == null) {
            logger.error("Error al notificar la tarea, el bloque candidato es nulo.");
            return;
        }

        MiningTask task = new MiningTask();
        task.setChallenge(hashChallenge);
        task.setBlock(blockCandidate);
        task.setRetries(retries);
        task.setEvent(ExchangeEvent.NEW_CANDIDATE_BLOCK);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE.PERSISTENT);

        Message message = jsonMessageConverter.toMessage(task, properties);

        rabbitTemplate.send(
                RabbitMQConfig.BLOCKCHAIN_EXCHANGE,
                "",
                message
        );
        logger.debug(
                "MiningTaskNotifier: Tarea de minería publicada para el bloque con index: " + blockCandidate.getIndex() +
                        " (hash ID: " + blockCandidate.getHash() + ", reintentos: " + retries + ", challenge: " + hashChallenge+ " ) al exchange '" + RabbitMQConfig.BLOCKCHAIN_EXCHANGE + "'."
        );
    }

    public void notifySolvedCandidateBlock(String preliminaryHash, String minerId) {
        MiningTaskStatus solvedTaskStatus = new MiningTaskStatus(
                ExchangeEvent.RESOLVED_CANDIDATE_BLOCK,
                preliminaryHash,
                minerId
        );

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE.PERSISTENT);

        Message message = jsonMessageConverter.toMessage(solvedTaskStatus, properties);
        rabbitTemplate.send(RabbitMQConfig.BLOCKCHAIN_EXCHANGE, "", message);
        logger.debug("MiningTaskNotifier: Notificado que el bloque: " + preliminaryHash + " fue resuelto por " + minerId + ". Evento RESOLVED_CANDIDATE_BLOCK publicado.");
    }

    public void notifyMiningTaskDropped(String preliminaryHash) {
        MiningTaskStatus droppedTaskStatus = new MiningTaskStatus(
                ExchangeEvent.CANDIDATE_BLOCK_DROPPED,
                preliminaryHash,
                ""
        );

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE.PERSISTENT);

        Message message = jsonMessageConverter.toMessage(droppedTaskStatus, properties);
        rabbitTemplate.send(RabbitMQConfig.BLOCKCHAIN_EXCHANGE, "", message);
        logger.debug("MiningTaskNotifier: Notificado que el bloque: " + preliminaryHash + " fue descartado. Evento CANDIDATE_BLOCK_DROPPED publicado.");
    }
}