package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.config.RabbitMQConfig;
import com.blockchain.miningpool.services.QueueAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueAdminImpl implements QueueAdmin {

    private final AmqpAdmin amqpAdmin;
    @Override
    public void purgeSubTasksQueue() {
        try {
            amqpAdmin.purgeQueue(RabbitMQConfig.POOL_TASKS_QUEUE, false);
            System.out.println("QueueAdminService: Cola '" + RabbitMQConfig.POOL_TASKS_QUEUE + "' purgada.");
        } catch (Exception e) {
            System.err.println("QueueAdminService: Error al purgar la cola '" + RabbitMQConfig.POOL_TASKS_QUEUE + "': " + e.getMessage());
        }
    }
}
