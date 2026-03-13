package com.enterprise.agent.service.chat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.enterprise.agent.service.chat.feign")
@ComponentScan(basePackages = "com.enterprise.agent")
@MapperScan({"com.enterprise.agent.data.mapper", "com.enterprise.agent.knowledge.repository"})
public class EapChatServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EapChatServiceApplication.class, args);
    }
}
