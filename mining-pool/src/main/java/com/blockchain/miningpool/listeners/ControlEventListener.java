package com.blockchain.miningpool.listeners;

import com.blockchain.miningpool.config.RabbitMQConfig;
import com.blockchain.miningpool.dtos.MiningTask;
import com.blockchain.miningpool.dtos.MiningTaskStatus;
import com.blockchain.miningpool.models.ExchangeEvent;
import com.blockchain.miningpool.services.MinerService;
import com.blockchain.miningpool.services.QueueAdmin;
import com.blockchain.miningpool.services.WorkerDispatcher;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ControlEventListener {

    private final WorkerDispatcher dispatcher;
    private final MinerService minerService;
    private final RabbitTemplate rabbitTemplate;
    private final QueueAdmin queueAdmin;

    @RabbitListener(
            queues = RabbitMQConfig.BLOCKS_CONTROL_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onControlEvent(Message rawMessage, Channel ch) throws Exception {
        long tag = rawMessage.getMessageProperties().getDeliveryTag();
        Object payload = null;

        try {
            payload = rabbitTemplate.getMessageConverter().fromMessage(rawMessage);
            System.out.println("Recibido evento de control de minería. Payload deserializado: " + payload);

            if (payload instanceof MiningTask task && task.getEvent() == ExchangeEvent.NEW_CANDIDATE_BLOCK) {
                long gpusMinersActive = minerService.getMinersCount();
                String challenge = task.getChallenge();

                long fullNonceRangeStart = 0;
                long fullNonceRangeEnd = estimateMaxNonceBasedOnChallenge(challenge);

                int numberOfDivisions = (gpusMinersActive > 0) ? (int) gpusMinersActive : 5;

                System.out.println("Dividiendo el rango de nonce entre " + numberOfDivisions + " workers. Rango total: " +
                        fullNonceRangeStart + " a " + fullNonceRangeEnd);

                if (numberOfDivisions < 1) numberOfDivisions = 1;

                long totalRange = fullNonceRangeEnd - fullNonceRangeStart + 1;
                long rangeSize = totalRange / numberOfDivisions;
                long remainder = totalRange % numberOfDivisions;

                long currentFrom = fullNonceRangeStart;

                for (int i = 0; i < numberOfDivisions; i++) {
                    long currentTo = currentFrom + rangeSize - 1;
                    if (i < remainder) {
                        currentTo++;
                    }
                    if (currentTo > fullNonceRangeEnd) {
                        currentTo = fullNonceRangeEnd;
                    }

                    System.out.println("Despachando subtarea #" + (i + 1) + ": nonce de " + currentFrom + " a " + currentTo);

                    dispatcher.dispatchSubTasks(
                            task.getBlock(),
                            task.getChallenge(),
                            currentFrom,
                            currentTo
                    );

                    currentFrom = currentTo + 1;
                    if (currentFrom > fullNonceRangeEnd) break;
                }

            } else if (payload instanceof MiningTaskStatus dropped &&
                    (dropped.getEvent() == ExchangeEvent.CANDIDATE_BLOCK_DROPPED ||
                            dropped.getEvent() == ExchangeEvent.RESOLVED_CANDIDATE_BLOCK)) {
                dispatcher.broadcastCancel(dropped.getPreliminaryHashBlockResolved());
                queueAdmin.purgeSubTasksQueue();
            } else {
                System.err.println("Tipo de payload inesperado: " + payload.getClass().getName());
            }

            ch.basicAck(tag, false);
        } catch (Exception e) {
            System.err.println("Error procesando evento de control de minería: " + e.getMessage());
            ch.basicNack(tag, false, false);
            throw e;
        }
    }

    private long estimateMaxNonceBasedOnChallenge(String challenge) {
        int zeros = 0;
        for (char c : challenge.toCharArray()) {
            if (c != '0') break;
            zeros++;
        }

        return switch (zeros) {
            case 0 -> 100_000L;
            case 1 -> 1_000_000L;
            case 2 -> 5_000_000L;
            case 3 -> 50_000_000L;
            case 4 -> 200_000_000L;
            case 5 -> 800_000_000L;
            default -> 0xFFFFFFFFL;
        };
    }
}