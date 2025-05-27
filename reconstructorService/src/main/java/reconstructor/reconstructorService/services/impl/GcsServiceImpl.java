package reconstructor.reconstructorService.services.impl;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reconstructor.reconstructorService.services.GcsService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GcsServiceImpl implements GcsService {

    private final Storage storage;

    @Override
    public byte[] descargarImagen(String bucketName, String objectName) {
        Blob blob = storage.get(bucketName, objectName);
        if (blob == null) {
            throw new RuntimeException("Objeto no encontrado en GCS: " + objectName);
        }
        return blob.getContent();
    }

    @Override
    public void subirImagen(String bucketName, String objectName, byte[] data, String contentType) {
        storage.create(
                Blob.newBuilder(bucketName, objectName).setContentType(contentType).build(),
                data
        );
    }

    @Override
    public List<String> listarImagenes(String bucketName, String prefix) {
        Bucket bucket = storage.get(bucketName);
        Page<Blob> blobs = bucket.list(Storage.BlobListOption.prefix(prefix));
        List<String> nombres = new ArrayList<>();
        for (Blob blob : blobs.iterateAll()) {
            nombres.add(blob.getName());
        }
        return nombres;
    }

    @Override
    public void borrarImagen(String bucketName, String objectName) {
        storage.delete(bucketName, objectName);
    }
}
