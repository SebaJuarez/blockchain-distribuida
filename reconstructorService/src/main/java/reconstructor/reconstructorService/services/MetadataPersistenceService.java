package reconstructor.reconstructorService.services;


import reconstructor.reconstructorService.dtos.ImageMetadata;

public interface MetadataPersistenceService {
    void persistMetadata(ImageMetadata metadata);

    ImageMetadata getMetadata(String id);

    void deleteMetadata(String id);

    void updateMetadata(String clave, ImageMetadata metadata);

    boolean exists(String id);

    boolean isComplete(String id);
}
