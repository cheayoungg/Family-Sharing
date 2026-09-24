package org.miniproject.homeproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HomeProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(HomeProjectApplication.class, args);
	}

}
