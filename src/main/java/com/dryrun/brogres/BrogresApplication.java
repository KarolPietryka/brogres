package com.dryrun.brogres;

import org.springframework.boot.SpringApplication;
import com.dryrun.brogres.config.BrogresSecurityProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(BrogresSecurityProperties.class)
public class BrogresApplication {

	public static void main(String[] args) {
		SpringApplication.run(BrogresApplication.class, args);
	}

}
