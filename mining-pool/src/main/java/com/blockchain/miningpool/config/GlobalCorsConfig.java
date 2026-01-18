package com.blockchain.miningpool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class GlobalCorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        config.addAllowedOriginPattern("*"); 
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(false);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}