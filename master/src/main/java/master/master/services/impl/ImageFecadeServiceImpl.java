package master.master.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import master.master.feignClients.MetadataClient;
import master.master.models.ImageMetadata;
import master.master.services.ImageFecadeService;
import master.master.services.ImageProcessingService;
import master.master.services.RabbitPublisherService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageFecadeServiceImpl implements ImageFecadeService {

    private final ImageProcessingService imageProcessingService;
    private final RabbitPublisherService rabbitPublisherService;
    private final MetadataClient metadataClient;

    @Override
    public void procesarYPublicar(MultipartFile imageFile, int partes, String id) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            log.warn("Imagen vacía o nula. No se procesará.");
            return;
        }

        log.info("Iniciando procesamiento y publicación de imagen en {} partes", partes);
        // pegar a endpoint del reconstructor
        ImageMetadata imageMetadata = new ImageMetadata(id, partes, 0, imageFile.getOriginalFilename(), imageFile.getContentType(), null);
        metadataClient.guardarMetaData(imageMetadata);

        List<byte[]> chunks = imageProcessingService.dividirImagen(imageFile, partes);
        rabbitPublisherService.publicarPartes(chunks, id);

        


        log.info("Se publicaron {} partes de la imagen en la cola RabbitMQ.", chunks.size());
    }
}
