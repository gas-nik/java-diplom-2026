package ru.diploma.raftcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ru.diploma.raftcache")
public class RaftCacheApplication {
	public static void main(String[] args) {
		SpringApplication.run(RaftCacheApplication.class, args);
	}

}
