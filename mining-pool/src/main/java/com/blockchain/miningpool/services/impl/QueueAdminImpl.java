package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.config.RabbitMQConfig;
import com.blockchain.miningpool.services.QueueAdmin;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueAdminImpl implements QueueAdmin {

    private static final Logger logger = LoggerFactory.getLogger(QueueAdminImpl.class);

    private final AmqpAdmin amqpAdmin;
    @Override
    public void purgeSubTasksQueue() {
        try {
            amqpAdmin.purgeQueue(RabbitMQConfig.POOL_TASKS_QUEUE, false);
            logger.debug("QueueAdminService: Cola '" + RabbitMQConfig.POOL_TASKS_QUEUE + "' purgada.");
        } catch (Exception e) {
            logger.error("QueueAdminService: Error al purgar la cola '" + RabbitMQConfig.POOL_TASKS_QUEUE + "': " + e.getMessage(), e);
        }
    }
}