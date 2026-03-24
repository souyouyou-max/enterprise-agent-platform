package com.sinosig.aip.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.sinosig.aip.scheduler.client")
public class AipSchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AipSchedulerApplication.class, args);
    }
}
