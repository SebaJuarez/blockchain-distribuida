package com.blockchain.miningpool.services.impl;

import com.blockchain.miningpool.config.RabbitMQConfig;
import com.blockchain.miningpool.dtos.CancelTask;
import com.blockchain.miningpool.dtos.SubTask;
import com.blockchain.miningpool.models.Block;
import com.blockchain.miningpool.services.WorkerDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkerDispatcherImpl implements WorkerDispatcher {

    private final RabbitTemplate rabbitTemplate;

    public void dispatchSubTasks(Block block, String challenge, long from, long to) {
        int parts = 0; // traer de redis
        long total = to - from;
        long step = Math.max(1, total / parts);

        for (int i = 0; i < parts; i++) {
            long start = from + i * step;
            long end   = (i == parts - 1) ? to : (start + step);
            SubTask sub = new SubTask(block, challenge, start, end);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.POOL_TASKS_EXCHANGE,
                    RabbitMQConfig.POOL_TASKS_ROUTING_KEY,
                    sub
            );
        }
    }

    public void broadcastCancel(String preliminaryHash) {
        CancelTask cancel = new CancelTask(preliminaryHash);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.POOL_CONTROL_EXCHANGE,
                "",
                cancel
        );
    }
}