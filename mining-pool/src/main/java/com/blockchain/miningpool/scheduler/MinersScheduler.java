package com.blockchain.miningpool.scheduler;

import com.blockchain.miningpool.services.MinerService;
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
    private final MinerService minerService;
    private final TaskScheduler taskScheduler;

    public MinersScheduler(TaskScheduler taskScheduler, MinerService minerService) {
        this.taskScheduler = taskScheduler;
        this.minerService = minerService;
    }

    @PostConstruct
    public void init() {
        taskScheduler.scheduleWithFixedDelay(this::checkGpuMiners, statusCheckInterval);
    }

    public void checkGpuMiners() {
        System.out.println("Checking GPU Miners...");
        minerService.checkKeepAliveMiners(statusCheckInterval);
    }
}

