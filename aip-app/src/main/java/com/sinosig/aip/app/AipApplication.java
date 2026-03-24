package com.sinosig.aip.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableFeignClients(basePackages = "com.sinosig.aip")
@ComponentScan(basePackages = "com.sinosig.aip")
@MapperScan({"com.sinosig.aip.data.mapper", "com.sinosig.aip.engine.rag.knowledge.repository"})
public class AipApplication {
    public static void main(String[] args) {
        SpringApplication.run(AipApplication.class, args);
    }
}
