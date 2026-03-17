package com.enterprise.agent.scheduler;

import com.enterprise.agent.scheduler.client.AuditEngineClient;
import com.enterprise.agent.scheduler.client.dto.AuditSummaryDTO;
import com.enterprise.agent.scheduler.client.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 审计引擎定时调度器
 *
 * <p>通过 Feign 调用 eap-app 的审计触发接口，不再直连数据库。
 * 调用链路：eap-scheduler → (HTTP/Feign) → eap-app → DB
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEngineScheduler {

    private final AuditEngineClient auditEngineClient;

    /** 申请编码列表，多个用英文逗号分隔，例如 APP001,APP002 */
    @Value("${eap.audit.apply-codes:APP001}")
    private String applyCodesConfig;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 定时全量审计任务
     */
    @Scheduled(fixedDelayString = "${eap.audit.interval-seconds:3600}000",
               initialDelayString = "${eap.audit.initial-delay-seconds:30}000")
    public void scheduledFullAudit() {
        List<String> applyCodes = Arrays.asList(applyCodesConfig.split(","));
        log.info("============================================================");
        log.info("[审计引擎调度] 定时任务启动 @ {}，申请编码：{}", LocalDateTime.now().format(FORMATTER), applyCodes);
        log.info("============================================================");

        for (String applyCode : applyCodes) {
            String code = applyCode.trim();
            if (code.isEmpty()) continue;
            triggerAuditForApply(code);
        }

        log.info("[审计引擎调度] 本轮定时任务完成 @ {}", LocalDateTime.now().format(FORMATTER));
    }

    /**
     * 通过 Feign 触发单申请编码的审计，并记录结果摘要
     */
    private void triggerAuditForApply(String applyCode) {
        log.info("[审计引擎] ─── 触发审计，申请编码：{} ───", applyCode);
        try {
            ResponseDTO<AuditSummaryDTO> response = auditEngineClient.triggerAudit(applyCode);
            if (response != null && response.getCode() == 200 && response.getData() != null) {
                summarize(applyCode, response.getData());
            } else {
                log.warn("[审计引擎] 申请[{}]审计接口返回异常：{}", applyCode,
                        response != null ? response.getMessage() : "null response");
            }
        } catch (Exception e) {
            log.error("[审计引擎] 申请[{}]审计调用失败：{}", applyCode, e.getMessage(), e);
        }
    }

    private void summarize(String applyCode, AuditSummaryDTO summary) {
        log.info("[审计引擎] 申请[{}]审计结果摘要：共{}条疑点（高风险{}条，中风险{}条，低风险{}条）",
                applyCode, summary.getTotalClues(),
                summary.getHighRisk(), summary.getMediumRisk(), summary.getLowRisk());
        if (summary.getHighRisk() > 0) {
            log.warn("[审计引擎] 申请[{}]存在{}条高风险疑点，请及时处理！",
                    applyCode, summary.getHighRisk());
        }
    }
}
