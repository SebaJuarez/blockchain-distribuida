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
        SubTask msg = new SubTask(block, challenge, from, to);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.POOL_TASKS_EXCHANGE,
                RabbitMQConfig.POOL_TASKS_ROUTING_KEY,
                msg
        );
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