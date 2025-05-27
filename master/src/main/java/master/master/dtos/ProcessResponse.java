package master.master.dtos;

public record ProcessResponse(
        String id,
        String message,
        Links links
) {
}