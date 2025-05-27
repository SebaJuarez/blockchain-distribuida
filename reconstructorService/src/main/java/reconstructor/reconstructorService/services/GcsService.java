package reconstructor.reconstructorService.services;

import java.util.List;

public interface GcsService {
    byte[] descargarImagen(String bucketName, String objectName);

    void subirImagen(String bucketName, String objectName, byte[] data, String contentType);

    List<String> listarImagenes(String bucketName, String prefix);

    void borrarImagen(String bucketName, String objectName);
}
