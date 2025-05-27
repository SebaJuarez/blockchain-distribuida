package reconstructor.reconstructorService.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import reconstructor.reconstructorService.dtos.ImageMetadata;
import reconstructor.reconstructorService.dtos.ParteProcesadaDTO;
import reconstructor.reconstructorService.services.GcsService;
import reconstructor.reconstructorService.services.ImageProcessingService;
import reconstructor.reconstructorService.services.MetadataPersistenceService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagenProcesadaListener {

    private final MetadataPersistenceService metadataPersistenceService;
    private final ImageProcessingService imageProcessingService;
    private final ObjectMapper mapper;
    private final GcsService gcsService;

    @Value("${gcs.bucket.name}")
    private String bucketName;

    @RabbitListener(queues = "image.processed.queue", containerFactory = "rabbitListenerContainerFactory")
    public void recibirParteProcesada(String mensajeJson, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            ParteProcesadaDTO dto = mapper.readValue(mensajeJson, ParteProcesadaDTO.class);
            if (dto == null || dto.getId() == null) {
                log.warn("Mensaje inválido o sin ID: {}", mensajeJson);
                channel.basicAck(tag, false);
                return;
            }
            String imagenId = dto.getId();

            ImageMetadata imageMetadata = metadataPersistenceService.getMetadata(imagenId);
            if (imageMetadata == null) {
                log.warn("No existe metadata para imagenId={}", imagenId);
                channel.basicAck(tag, false);
                return;
            }

            log.info("Recibida parte #{} para imagenId={}", dto.getIndice(), imagenId);

            ImageMetadata updated = ImageMetadata.builder().id(imagenId).nombreImagen(imageMetadata.nombreImagen()).contentType(imageMetadata.contentType()).partes(imageMetadata.partes()).partesProcesadas(imageMetadata.partesProcesadas() + 1).build();
            metadataPersistenceService.updateMetadata(imagenId, updated);

            if (metadataPersistenceService.isComplete(imagenId)) {
                List<byte[]> partesBytes = new ArrayList<>();
                for (int i = 0; i < updated.partes(); i++) {
                    String blobName = imagenId + "_" + i + ".jpg";
                    partesBytes.add(gcsService.descargarImagen(bucketName, blobName));
                    gcsService.borrarImagen(bucketName, blobName);
                }

                ImageMetadata meta = metadataPersistenceService.getMetadata(imagenId);
                String extension = meta.contentType().split("/")[1];

                byte[] merged = imageProcessingService.unirImagenes(partesBytes, extension);
                String outName = imagenId;
                gcsService.subirImagen(bucketName, outName, merged, meta.contentType());
                log.info("Imagen reconstruida y guardada en gs://{}/{}", bucketName, outName);
            }
            channel.basicAck(tag, false);
        } catch (IOException e) {
            log.error("Error I/O al procesar mensaje: {}", e.getMessage(), e);
            try {
                channel.basicNack(tag, false, false);
            } catch (IOException ignored) {
            }
        } catch (Exception e) {
            log.error("Excepción inesperada en listener", e);
            try {
                channel.basicNack(tag, false, false);
            } catch (IOException ignored) {
            }
        }
    }
}