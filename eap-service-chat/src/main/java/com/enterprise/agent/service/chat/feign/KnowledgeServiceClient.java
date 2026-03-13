package com.enterprise.agent.service.chat.feign;

import com.enterprise.agent.common.core.response.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 知识问答服务 Feign 客户端
 * 用于 eap-service-chat 跨服务调用 eap-service-knowledge
 */
@FeignClient(name = "eap-service-knowledge")
public interface KnowledgeServiceClient {

    @PostMapping("/api/v1/knowledge/ask")
    ResponseResult<String> ask(@RequestBody Map<String, String> request);
}
