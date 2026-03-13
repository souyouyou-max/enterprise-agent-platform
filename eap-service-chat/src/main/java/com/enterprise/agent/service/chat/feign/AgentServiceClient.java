package com.enterprise.agent.service.chat.feign;

import com.enterprise.agent.common.core.response.ResponseResult;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Agent 服务 Feign 客户端
 * 用于 eap-service-chat 跨服务调用 eap-service-agent
 */
@FeignClient(name = "eap-service-agent")
public interface AgentServiceClient {

    @PostMapping("/api/v1/tasks")
    ResponseResult<AgentTaskVO> createTask(@RequestBody CreateTaskRequest request);

    @GetMapping("/api/v1/tasks/{id}")
    ResponseResult<AgentTaskVO> getTask(@PathVariable("id") Long id);

    @Data
    class CreateTaskRequest {
        private String taskName;
        private String goal;
    }

    @Data
    class AgentTaskVO {
        private Long id;
        private String taskName;
        private String goal;
        private String status;
        private String finalReport;
    }
}
