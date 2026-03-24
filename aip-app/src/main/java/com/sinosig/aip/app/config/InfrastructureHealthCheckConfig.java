package com.sinosig.aip.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 基础组件启动健康检测
 * 若 Redis 连接失败，则阻止服务进入就绪状态并抛出异常
 */
@Slf4j
@Configuration
public class InfrastructureHealthCheckConfig implements ApplicationListener<ApplicationReadyEvent> {

    private final RedisConnectionFactory redisConnectionFactory;

    public InfrastructureHealthCheckConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        checkRedis();
    }

    private void checkRedis() {
        log.info("[HealthCheck] 检测 Redis 连通性...");
        try {
            String pong = redisConnectionFactory.getConnection().ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("Redis ping 响应异常: " + pong);
            }
            log.info("[HealthCheck] Redis 连接正常");
        } catch (Exception e) {
            log.error("[HealthCheck] Redis 连接失败，服务终止启动: {}", e.getMessage());
            throw new IllegalStateException("Redis 不可用，拒绝启动: " + e.getMessage(), e);
        }
    }
}
