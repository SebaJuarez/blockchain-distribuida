package master.master.controllers;

import lombok.RequiredArgsConstructor;
import master.master.dtos.Links;
import master.master.dtos.ProcessResponse;
import master.master.dtos.StatusResponse;
import master.master.feignClients.ImageClient;
import master.master.services.ImageFecadeService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/task")
public class ImageController {

    private final ImageFecadeService imageFacadeService;
    private final ImageClient imageClient;

    @PostMapping("/processAndPublish")
    public ResponseEntity<ProcessResponse> processAndPublish(
            @RequestParam("image") MultipartFile image,
            @RequestParam("partes") int partes) throws IOException {

        String id = String.format("%s_%s", UUID.randomUUID(), image.getOriginalFilename());

        imageFacadeService.procesarYPublicar(image, partes, id);

        String message = "La imagen se ha procesado y se ha publicado en la cola. "
                + "Utilice el ID para consultar el estado (GET /task/status/{id}) "
                + "y, una vez completada la operación, para obtener la imagen resultante (GET /task/images/{id}).";
        Links links = new Links("/task/status/" + id, "/task/images/" + id);
        ProcessResponse response = new ProcessResponse(id, message, links);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/images/{idImagen}")
    public ResponseEntity<ByteArrayResource> getImage(@PathVariable String idImagen) {
        byte[] data = imageClient.obtenerImagen(idImagen);

        String ext = idImagen.contains(".")
                ? idImagen.substring(idImagen.lastIndexOf('.') + 1).toLowerCase()
                : "";
        MediaType mediaType = switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + idImagen + "\"")
                .contentLength(data.length)
                .contentType(mediaType)
                .body(new ByteArrayResource(data));
    }

    @GetMapping("/status/{idImagen}")
    public ResponseEntity<StatusResponse> getStatus(@PathVariable String idImagen) {
        StatusResponse status = imageClient.obtenerEstadoImagen(idImagen);
        return ResponseEntity.ok(status);
    }
}
