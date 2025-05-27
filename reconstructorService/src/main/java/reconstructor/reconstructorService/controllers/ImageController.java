package reconstructor.reconstructorService.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reconstructor.reconstructorService.dtos.ErrorResponse;
import reconstructor.reconstructorService.dtos.ImageMetadata;
import reconstructor.reconstructorService.dtos.StatusResponse;
import reconstructor.reconstructorService.exceptions.ResourceNotFoundException;
import reconstructor.reconstructorService.services.GcsService;
import reconstructor.reconstructorService.services.MetadataPersistenceService;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final GcsService gcsService;
    private final MetadataPersistenceService metadataPersistenceService;
    @Value("${gcs.bucket.name}")
    private String bucketName;

    @GetMapping("/{idImagen}")
    public ResponseEntity<ByteArrayResource> obtenerImagen(@PathVariable String idImagen) {
        try {
            byte[] data = gcsService.descargarImagen(bucketName, idImagen);
            if (data == null || data.length == 0) {
                throw new ResourceNotFoundException("La imagen con id " + idImagen + " no se encontró.");
            }

            String ext = idImagen.contains(".")
                    ? idImagen.substring(idImagen.lastIndexOf('.') + 1).toLowerCase()
                    : "";
            MediaType mediaType = switch (ext) {
                case "png" -> MediaType.IMAGE_PNG;
                case "gif" -> MediaType.IMAGE_GIF;
                case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
                default -> MediaType.APPLICATION_OCTET_STREAM;
            };

            metadataPersistenceService.deleteMetadata(idImagen);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + idImagen + "\"")
                    .contentLength(data.length)
                    .contentType(mediaType)
                    .body(new ByteArrayResource(data));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/status/{idImagen}")
    public ResponseEntity<?> obtenerEstadoImagen(@PathVariable String idImagen) {

        ImageMetadata meta = metadataPersistenceService.getMetadata(idImagen);
        if (meta == null) {
            ErrorResponse error = new ErrorResponse("Metadata not found", "No se encontró metadata para el id: " + idImagen);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        boolean completa = metadataPersistenceService.isComplete(idImagen);
        String estado = completa ? "COMPLETA" : "EN PROCESO";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StatusResponse(idImagen, estado));
    }
}