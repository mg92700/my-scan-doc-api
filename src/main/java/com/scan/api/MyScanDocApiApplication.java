package com.scan.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MyScanDocApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyScanDocApiApplication.class, args);
	}

}
