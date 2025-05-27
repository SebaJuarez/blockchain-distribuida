package master.master.services;

import java.util.List;

public interface RabbitPublisherService {
    void publicarPartes(List<byte[]> partes, String imageId);
}
