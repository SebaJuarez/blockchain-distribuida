package com.blockchain.miningpool.scheduler;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;


@Component
@EnableScheduling
public class MinersScheduler {

    @Value("${miner.status-check-interval}")
    private long statusCheckInterval;

    private final TaskScheduler taskScheduler;

    public MinersScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void init() {
        taskScheduler.scheduleWithFixedDelay(this::checkGpuMiners, statusCheckInterval);
    }

    public void checkGpuMiners() {
        System.out.println("Checking GPU Miners...");
        // ver si pasaron mas de 10 segundos para los mineros registrados, si pasaron los 10 segundos, eliminarlos

        // pregunto si la tabla quedo vacia, si es asi levanto cpu's en la nube.
    }
}

