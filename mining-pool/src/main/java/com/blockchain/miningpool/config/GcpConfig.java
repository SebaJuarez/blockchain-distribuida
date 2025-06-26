package com.blockchain.miningpool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.services.compute.Compute;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.http.HttpCredentialsAdapter;

@Configuration
public class GcpConfig {
    @Bean
    public Compute compute(GoogleCredentials creds) throws Exception {
        return new Compute.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(creds))
                .setApplicationName("miner-service")
                .build();
    }
}
