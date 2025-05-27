package reconstructor.reconstructorService.configurations;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcsConfig {

    @Bean
    public Storage gcsStorage() {
        // Usa GOOGLE_APPLICATION_CREDENTIALS/autodetect
        return StorageOptions.getDefaultInstance().getService();
    }
}