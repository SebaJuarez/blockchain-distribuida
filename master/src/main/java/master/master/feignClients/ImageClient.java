package master.master.feignClients;


import master.master.configurations.ImageClientConfig;
import master.master.dtos.StatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "${metadata.service.name:reconstructor}", contextId = "imageClient", path = "/api/images", configuration = ImageClientConfig.class)
public interface ImageClient {

    @GetMapping(value = "/{idImagen}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] obtenerImagen(@PathVariable("idImagen") String idImagen);

    @GetMapping(value = "/status/{idImagen}", produces = MediaType.APPLICATION_JSON_VALUE)
    StatusResponse obtenerEstadoImagen(@PathVariable("idImagen") String idImagen);

}
