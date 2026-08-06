package com.blockchain.coordinator.services;

import com.blockchain.coordinator.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.stereotype.Service;

@Service
public class QueueAdminService {

    private static final Logger logger = LoggerFactory.getLogger(QueueAdminService.class);

    private final AmqpAdmin amqpAdmin;

    public QueueAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    public void purgeBlocksQueue() {
        try {
            amqpAdmin.purgeQueue(RabbitMQConfig.BLOCKS_QUEUE, false);
            logger.debug("QueueAdminService: Cola '" + RabbitMQConfig.BLOCKS_QUEUE + "' purgada.");
        } catch (Exception e) {
            logger.error("QueueAdminService: Error al purgar la cola '" + RabbitMQConfig.BLOCKS_QUEUE + "': " + e.getMessage(), e);
        }
    }
}