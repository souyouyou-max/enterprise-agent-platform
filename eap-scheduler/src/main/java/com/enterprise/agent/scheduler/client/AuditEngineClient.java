package com.enterprise.agent.scheduler.client;

import com.enterprise.agent.scheduler.client.dto.AuditSummaryDTO;
import com.enterprise.agent.scheduler.client.dto.ResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * eap-app 审计引擎 Feign 客户端
 * 通过 Nacos 服务发现自动路由，无需硬编码地址
 */
@FeignClient(name = "eap-app", path = "/api/v1/audit-engine")
public interface AuditEngineClient {

    /**
     * 触发指定机构的完整审计
     */
    @PostMapping("/trigger/{applyCode}")
    ResponseDTO<AuditSummaryDTO> triggerAudit(@PathVariable("applyCode") String applyCode);
}
