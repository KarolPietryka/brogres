package com.dryrun.brogres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BrogresApplication {

	public static void main(String[] args) {
		SpringApplication.run(BrogresApplication.class, args);
	}

}
