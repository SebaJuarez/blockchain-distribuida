package com.blockchain.coordinator;

import com.blockchain.coordinator.services.BlockService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@RequiredArgsConstructor
public class CoordinatorNodeApplication {

	private final BlockService blockService;

	public static void main(String[] args) {
		SpringApplication.run(CoordinatorNodeApplication.class, args);
	}

	@PostConstruct
	public void init() {
		blockService.init();
	}
}