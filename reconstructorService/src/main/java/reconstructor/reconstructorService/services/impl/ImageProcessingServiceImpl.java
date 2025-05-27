package reconstructor.reconstructorService.services.impl;

import org.springframework.stereotype.Service;
import reconstructor.reconstructorService.services.ImageProcessingService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImageProcessingServiceImpl implements ImageProcessingService {

    @Override
    public byte[] unirImagenes(List<byte[]> partes, String format) throws IOException {
        if (partes == null || partes.isEmpty()) {
            throw new IllegalArgumentException("La lista de partes no puede estar vacía");
        }

        // Reconstruir BufferedImages de los bytes
        List<BufferedImage> imagenesLista = new ArrayList<>(partes.size());
        for (byte[] parte : partes) {
            imagenesLista.add(ImageIO.read(new ByteArrayInputStream(parte)));
        }

        int n = imagenesLista.size();
        int rows = (int) Math.ceil(Math.sqrt(n));
        int cols = (int) Math.ceil((double) n / rows);

        // Calcular anchos/altos máximos por columna/fila
        int[] colWidths = new int[cols];
        int[] rowHeights = new int[rows];
        for (int i = 0; i < n; i++) {
            BufferedImage img = imagenesLista.get(i);
            int r = i / cols;
            int c = i % cols;
            colWidths[c] = Math.max(colWidths[c], img.getWidth());
            rowHeights[r] = Math.max(rowHeights[r], img.getHeight());
        }

        // Tamaño total del lienzo
        int totalW = 0;
        for (int w : colWidths) totalW += w;
        int totalH = 0;
        for (int h : rowHeights) totalH += h;

        // Crear imagen final
        BufferedImage imagenFinal = new BufferedImage(totalW, totalH, imagenesLista.get(0).getType());
        Graphics2D g = imagenFinal.createGraphics();

        // Dibujar cada fragmento en su posición
        int yOff = 0;
        int index = 0;
        for (int ry = 0; ry < rows; ry++) {
            int xOff = 0;
            for (int cx = 0; cx < cols && index < n; cx++) {
                BufferedImage img = imagenesLista.get(index++);
                g.drawImage(img, xOff, yOff, null);
                xOff += colWidths[cx];
            }
            yOff += rowHeights[ry];
        }
        g.dispose();

        // Escribir imagen final a bytes
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(imagenFinal, format, baos);
            return baos.toByteArray();
        }
    }
}
