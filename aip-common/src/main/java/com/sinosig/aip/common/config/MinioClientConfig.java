package com.sinosig.aip.common.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO Client 配置（按需启用）
 */
@Configuration
@ConfigurationProperties(prefix = "aip.minio")
@Data
public class MinioClientConfig {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
    private boolean enabled = false;

    @Bean
    @ConditionalOnProperty(prefix = "aip.minio", name = "enabled", havingValue = "true")
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

