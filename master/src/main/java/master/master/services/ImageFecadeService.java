package master.master.services;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ImageFecadeService {
    void procesarYPublicar(MultipartFile imageFile, int partes, String id) throws IOException;
}
