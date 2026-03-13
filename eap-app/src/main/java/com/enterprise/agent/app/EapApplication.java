package com.enterprise.agent.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@ComponentScan(basePackages = "com.enterprise.agent")
@MapperScan("com.enterprise.agent.data.mapper")
public class EapApplication {
    public static void main(String[] args) {
        SpringApplication.run(EapApplication.class, args);
    }
}
