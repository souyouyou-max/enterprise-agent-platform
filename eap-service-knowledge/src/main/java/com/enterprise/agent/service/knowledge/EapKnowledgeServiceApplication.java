package com.enterprise.agent.service.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.enterprise.agent")
@MapperScan("com.enterprise.agent.knowledge.repository")
public class EapKnowledgeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EapKnowledgeServiceApplication.class, args);
    }
}
