package com.blockchain.miningpool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MiningPoolApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiningPoolApplication.class, args);
	}

}
