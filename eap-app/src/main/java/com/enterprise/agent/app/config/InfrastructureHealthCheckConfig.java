package com.enterprise.agent.app.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基础组件启动健康检测
 * 若 Redis 或 Kafka 连接失败，则阻止服务进入就绪状态并抛出异常
 */
@Slf4j
@Configuration
public class InfrastructureHealthCheckConfig implements ApplicationListener<ApplicationReadyEvent> {

    private final RedisConnectionFactory redisConnectionFactory;

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    public InfrastructureHealthCheckConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        checkRedis();
        checkKafka();
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

    private void checkKafka() {
        log.info("[HealthCheck] 检测 Kafka 连通性...");
        Map<String, Object> config = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000",
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000"
        );
        try (AdminClient adminClient = AdminClient.create(config)) {
            adminClient.listTopics().names().get(5, TimeUnit.SECONDS);
            log.info("[HealthCheck] Kafka 连接正常");
        } catch (Exception e) {
            log.error("[HealthCheck] Kafka 连接失败，服务终止启动: {}", e.getMessage());
            throw new IllegalStateException("Kafka 不可用，拒绝启动: " + e.getMessage(), e);
        }
    }
}
