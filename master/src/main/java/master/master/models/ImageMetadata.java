package master.master.models;

import lombok.Builder;

@Builder
public record ImageMetadata(
        String id,
        int partes,
        int partesProcesadas,
        String nombreImagen,
        String contentType,
        String url
) {
}
