package reconstructor.reconstructorService.dtos;

public record ErrorResponse(
        String error,
        String message
) {
}