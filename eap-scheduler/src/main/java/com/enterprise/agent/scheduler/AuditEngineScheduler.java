package com.enterprise.agent.scheduler;

import com.enterprise.agent.data.entity.ClueResult;
import com.enterprise.agent.business.screening.service.AuditEngineService;
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
 * <p>定时驱动规则SQL分析引擎按以下数据流执行：
 * <pre>
 * 外部平台（Mock）→ 数据接入层（适配器）→ 统一数据仓库 → 规则SQL引擎 → 疑点结果表 → Agent读取分析
 * </pre>
 *
 * <p>调度策略：
 * <ul>
 *   <li>数据同步 + 规则检测：每小时执行一次（生产建议：每天凌晨2点）</li>
 *   <li>可通过 {@code eap.audit.org-codes} 配置被审计机构列表</li>
 *   <li>通过 {@link AuditEngineController} 提供手动触发 API</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEngineScheduler {

    private final AuditEngineService auditEngineService;

    /**
     * 被审计机构编码列表，逗号分隔，默认 ORG001
     * 可在 application.yml 中配置：eap.audit.org-codes=ORG001,ORG002
     */
    @Value("${eap.audit.org-codes:ORG001}")
    private String orgCodesConfig;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 定时全量审计任务
     * 默认每小时执行一次（fixedDelay=3600秒，initialDelay=30秒启动后延迟执行）。
     * 生产环境建议改为 cron = "0 0 2 * * ?" 每天凌晨2点执行。
     */
    @Scheduled(fixedDelayString = "${eap.audit.interval-seconds:3600}000",
               initialDelayString = "${eap.audit.initial-delay-seconds:30}000")
    public void scheduledFullAudit() {
        List<String> orgCodes = Arrays.asList(orgCodesConfig.split(","));
        String startTime = LocalDateTime.now().format(FORMATTER);

        log.info("============================================================");
        log.info("[审计引擎调度] 定时任务启动 @ {}，被审计机构：{}", startTime, orgCodes);
        log.info("============================================================");

        for (String orgCode : orgCodes) {
            String trimmedCode = orgCode.trim();
            if (trimmedCode.isEmpty()) continue;
            runAuditForOrg(trimmedCode);
        }

        log.info("[审计引擎调度] 本轮定时任务完成 @ {}", LocalDateTime.now().format(FORMATTER));
    }

    /**
     * 对单个机构执行完整审计（数据同步 + 规则检测）
     */
    public List<ClueResult> runAuditForOrg(String orgCode) {
        log.info("[审计引擎] ─── 开始处理机构：{} ───", orgCode);
        try {
            List<ClueResult> clues = auditEngineService.fullAudit(orgCode);
            summarize(orgCode, clues);
            return clues;
        } catch (Exception e) {
            log.error("[审计引擎] 机构[{}]审计执行失败：{}", orgCode, e.getMessage(), e);
            return List.of();
        }
    }

    private void summarize(String orgCode, List<ClueResult> clues) {
        long high = clues.stream().filter(c -> "HIGH".equals(c.getRiskLevel())).count();
        long medium = clues.stream().filter(c -> "MEDIUM".equals(c.getRiskLevel())).count();
        long low = clues.stream().filter(c -> "LOW".equals(c.getRiskLevel())).count();

        log.info("[审计引擎] 机构[{}]审计结果摘要：共{}条疑点（高风险{}条，中风险{}条，低风险{}条）",
                orgCode, clues.size(), high, medium, low);

        if (!clues.isEmpty()) {
            log.warn("[审计引擎] 机构[{}]疑点线索详情：", orgCode);
            clues.forEach(c -> log.warn("  [{}][{}] {} - 涉及金额：{} 元",
                    c.getRiskLevel(), c.getClueType(), c.getClueTitle(), c.getRelatedAmount()));
        }
    }
}
