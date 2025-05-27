package reconstructor.reconstructorService.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParteProcesadaDTO {
    private String id;
    private int indice;
    private String parteProcesada;
}