package com.learnplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LearnForgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(LearnForgeApplication.class, args);
    }
}
