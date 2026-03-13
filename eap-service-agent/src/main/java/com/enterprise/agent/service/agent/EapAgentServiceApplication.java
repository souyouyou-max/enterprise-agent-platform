package com.enterprise.agent.service.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableFeignClients
@ComponentScan(basePackages = "com.enterprise.agent")
@MapperScan("com.enterprise.agent.data.mapper")
public class EapAgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EapAgentServiceApplication.class, args);
    }
}
