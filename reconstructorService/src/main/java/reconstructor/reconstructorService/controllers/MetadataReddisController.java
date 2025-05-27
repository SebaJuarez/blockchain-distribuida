package reconstructor.reconstructorService.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reconstructor.reconstructorService.dtos.ImageMetadata;
import reconstructor.reconstructorService.services.MetadataPersistenceService;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataReddisController {

    private final MetadataPersistenceService metadataPersistenceService;

    @PostMapping("/guardar")
    public void guardarMetaData(@RequestBody ImageMetadata metadata) {
        metadataPersistenceService.persistMetadata(metadata);
    }

}
