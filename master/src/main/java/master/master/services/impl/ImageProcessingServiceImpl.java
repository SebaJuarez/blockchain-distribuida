package master.master.services.impl;

import master.master.services.ImageProcessingService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ImageProcessingServiceImpl implements ImageProcessingService {

    @Override
    public List<byte[]> dividirImagen(MultipartFile image, int partes) throws IOException {
        if (partes <= 0) {
            throw new IllegalArgumentException("El número de partes debe ser mayor o igual a 1");
        }

        // Leer imagen original
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(image.getBytes()));
        int width = original.getWidth();
        int height = original.getHeight();
        int type = original.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : original.getType();

        // Cálculo de filas y columnas usando ceil
        int rows = (int) Math.ceil(Math.sqrt(partes));
        int cols = (int) Math.ceil((double) partes / rows);

        // Tamaño base de cada fragmento
        int baseW = width / cols;
        int baseH = height / rows;

        // Determinar formato (extensión) de la imagen
        String format = Optional.ofNullable(image.getOriginalFilename())
                .filter(fn -> fn.contains("."))
                .map(fn -> fn.substring(fn.lastIndexOf('.') + 1))
                .orElse("png");

        List<byte[]> partesImagenes = new ArrayList<>(partes);
        int count = 0;

        // Dividir la imagen
        for (int ry = 0; ry < rows && count < partes; ry++) {
            for (int cx = 0; cx < cols && count < partes; cx++) {
                // Ajuste en bordes
                int w = (cx == cols - 1) ? width - cx * baseW : baseW;
                int h = (ry == rows - 1) ? height - ry * baseH : baseH;

                BufferedImage subImage = new BufferedImage(w, h, type);
                Graphics2D g = subImage.createGraphics();
                g.drawImage(original,
                        0, 0, w, h,
                        cx * baseW, ry * baseH,
                        cx * baseW + w, ry * baseH + h,
                        null);
                g.dispose();

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(subImage, format, baos);
                    partesImagenes.add(baos.toByteArray());
                }
                count++;
            }
        }

        return partesImagenes;
    }
}
