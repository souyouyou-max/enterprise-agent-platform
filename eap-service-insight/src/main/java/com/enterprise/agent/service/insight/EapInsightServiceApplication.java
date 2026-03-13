package com.enterprise.agent.service.insight;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.enterprise.agent")
@MapperScan("com.enterprise.agent.data.mapper")
public class EapInsightServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EapInsightServiceApplication.class, args);
    }
}
