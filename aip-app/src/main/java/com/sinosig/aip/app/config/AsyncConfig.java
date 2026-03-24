package com.sinosig.aip.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步线程池统一配置
 * <p>
 * OCR 批量处理使用独立线程池（与主请求线程隔离）。
 */
@Configuration
public class AsyncConfig {

    /**
     * OCR Pipeline 线程池
     * 文件处理以 I/O 为主，可承受更高并发；队列容量大以应对批量提交。
     */
    @Bean(name = "ocrPipelineExecutor")
    public Executor ocrPipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(40);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ocr-pipeline-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
