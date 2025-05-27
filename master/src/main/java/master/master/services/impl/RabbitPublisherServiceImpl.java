package master.master.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import master.master.configurations.RabbitConfig;
import master.master.services.RabbitPublisherService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class RabbitPublisherServiceImpl implements RabbitPublisherService {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publicarPartes(List<byte[]> partes, String imageId) {

        if (partes == null || partes.isEmpty()) {
            log.warn("No se enviaron partes de imagen, la lista está vacía o es null.");
            return;
        }
        log.info("Publicando {} partes a la cola.", partes.size());
        for (int i = 0; i < partes.size(); i++) {
            Map<String, Object> mensaje = new HashMap<>();
            mensaje.put("id", imageId);
            mensaje.put("indice", i);
            mensaje.put("parte", Base64.getEncoder().encodeToString(partes.get(i)));


            rabbitTemplate.convertAndSend(
                    RabbitConfig.TASK_EXCHANGE,
                    RabbitConfig.TASK_ROUTING_KEY,
                    mensaje
            );
            log.info("Publicado chunk #{} a la cola.", i);
        }
    }
}
