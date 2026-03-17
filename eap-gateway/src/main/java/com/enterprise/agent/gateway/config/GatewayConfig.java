package com.enterprise.agent.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 网关配置：限流 Key 解析器 + 熔断 Fallback
 */
@Configuration
public class GatewayConfig {

    /**
     * 限流 Key：按客户端 IP 隔离，防止单 IP 打爆接口
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * 熔断 Fallback：下游不可用时返回统一降级响应
     */
    @Bean
    public RouterFunction<ServerResponse> fallbackRoute() {
        return RouterFunctions.route()
                .GET("/fallback", request -> fallbackResponse())
                .POST("/fallback", request -> fallbackResponse())
                .PUT("/fallback", request -> fallbackResponse())
                .DELETE("/fallback", request -> fallbackResponse())
                .build();
    }

    private Mono<ServerResponse> fallbackResponse() {
        Map<String, Object> body = Map.of(
                "code", 503,
                "message", "服务暂时不可用，请稍后重试",
                "timestamp", System.currentTimeMillis()
        );
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }
}
