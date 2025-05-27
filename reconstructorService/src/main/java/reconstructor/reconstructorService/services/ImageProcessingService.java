package reconstructor.reconstructorService.services;

import java.io.IOException;
import java.util.List;

public interface ImageProcessingService {

    byte[] unirImagenes(List<byte[]> imagenes, String format) throws IOException;

}
