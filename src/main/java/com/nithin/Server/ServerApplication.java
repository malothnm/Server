package com.nithin.Server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@Slf4j
public class ServerApplication {

	public static void main(String[] args) throws InterruptedException {

		ConfigurableApplicationContext ctx = SpringApplication.run(ServerApplication.class, args);




	}

}


