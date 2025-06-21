package com.blockchain.miningpool.listeners;

import com.blockchain.miningpool.config.RabbitMQConfig;
import com.blockchain.miningpool.dtos.MiningTask;
import com.blockchain.miningpool.dtos.MiningTaskStatus;
import com.blockchain.miningpool.models.ExchangeEvent;
import com.blockchain.miningpool.services.WorkerDispatcher;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ControlEventListener {

    private final WorkerDispatcher dispatcher;

    @RabbitListener(
            queues = RabbitMQConfig.BLOCKS_CONTROL_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onControlEvent(Message raw, @Payload Object payload, Channel ch) throws Exception {
        long tag = raw.getMessageProperties().getDeliveryTag();
        try {
            if (payload instanceof MiningTask task
                    && task.getEvent() == ExchangeEvent.NEW_CANDIDATE_BLOCK) {
                dispatcher.dispatchSubTasks(
                        task.getBlock(),
                        task.getChallenge(),
                        task.getFrom(),
                        task.getTo()
                );

            } else if (payload instanceof MiningTaskStatus dropped
                    && dropped.getEvent() == ExchangeEvent.CANDIDATE_BLOCK_DROPPED) {
                dispatcher.broadcastCancel(dropped.getPreliminaryHashBlockResolved());
            }
            ch.basicAck(tag, false);
        } catch (Exception e) {
            ch.basicNack(tag, false, false);
            throw e;
        }
    }
}
