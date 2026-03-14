package com.enterprise.agent.scheduler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.enterprise.agent")
@MapperScan("com.enterprise.agent.data.mapper")
public class EapSchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EapSchedulerApplication.class, args);
    }
}
