package master.master.feignClients;

import master.master.configurations.ImageClientConfig;
import master.master.models.ImageMetadata;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "${metadata.service.name:reconstructor}", contextId = "metadataClient", path = "/api/metadata", configuration = ImageClientConfig.class)
public interface MetadataClient {

    @PostMapping(
            value = "/guardar",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    void guardarMetaData(@RequestBody ImageMetadata metadata);
}
