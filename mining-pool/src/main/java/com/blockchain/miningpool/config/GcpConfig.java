package com.blockchain.miningpool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.services.compute.ComputeScopes;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
@Profile("gcp")
public class GcpConfig {

    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        // usa ADC (Application Default Credentials) y le da el scope necesario
        return GoogleCredentials.getApplicationDefault()
                .createScoped(Collections.singletonList(ComputeScopes.CLOUD_PLATFORM));
    }

    @Bean
    public Compute compute(GoogleCredentials creds) throws GeneralSecurityException, IOException {
        return new Compute.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(creds))
                .setApplicationName("miner-service")
                .build();
    }
}