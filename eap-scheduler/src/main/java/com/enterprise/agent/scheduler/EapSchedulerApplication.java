package com.enterprise.agent.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.enterprise.agent.scheduler.client")
public class EapSchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EapSchedulerApplication.class, args);
    }
}
