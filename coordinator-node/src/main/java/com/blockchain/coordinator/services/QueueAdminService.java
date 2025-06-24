package com.blockchain.coordinator.services;

import com.blockchain.coordinator.config.RabbitMQConfig;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.stereotype.Service;

@Service
public class QueueAdminService {

    private final AmqpAdmin amqpAdmin;

    public QueueAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    public void purgeBlocksQueue() {
        try {
            amqpAdmin.purgeQueue(RabbitMQConfig.BLOCKS_QUEUE, false);
            System.out.println("QueueAdminService: Cola '" + RabbitMQConfig.BLOCKS_QUEUE + "' purgada.");
        } catch (Exception e) {
            System.err.println("QueueAdminService: Error al purgar la cola '" + RabbitMQConfig.BLOCKS_QUEUE + "': " + e.getMessage());
        }
    }
}