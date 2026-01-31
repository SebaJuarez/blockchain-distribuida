package com.blockchain.coordinator.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class LoggingInitializer {

    @PostConstruct
    public void init() {
        MDC.put("app", "coordinator-node");
    }
}
