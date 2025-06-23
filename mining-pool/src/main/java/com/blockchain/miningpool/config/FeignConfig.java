package com.blockchain.miningpool.config;

import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new ErrorDecoder() {
            private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
            @Override
            public Exception decode(String methodKey, Response response) {
                if (response.status() == 400) {
                    return FeignException.errorStatus(methodKey, response);
                }
                return defaultDecoder.decode(methodKey, response);
            }

        };
    }
}
