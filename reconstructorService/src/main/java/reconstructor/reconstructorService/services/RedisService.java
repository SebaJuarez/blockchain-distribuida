package reconstructor.reconstructorService.services;

import reconstructor.reconstructorService.dtos.ImageMetadata;

public interface RedisService {
    void guardarMetaData(String clave, ImageMetadata valor);

    ImageMetadata obtenerMetaData(String clave);

    void eliminarMetaData(String clave);

    void actualizarMetaData(String clave, ImageMetadata valor);
}
