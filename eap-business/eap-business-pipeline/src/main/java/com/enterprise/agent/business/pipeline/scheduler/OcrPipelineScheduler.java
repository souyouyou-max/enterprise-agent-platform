package com.enterprise.agent.business.pipeline.scheduler;

import com.enterprise.agent.business.pipeline.config.EapPipelineProperties;
import com.enterprise.agent.business.pipeline.config.PipelineEffectiveConfigResolver;
import com.enterprise.agent.business.pipeline.service.OcrPipelineService;
import com.enterprise.agent.data.entity.OcrPipelineBatch;
import com.enterprise.agent.data.mapper.OcrPipelineBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

/**
 * OCR 流水线定时补偿调度器
 *
 * <h3>作用</h3>
 * 当流水线因进程重启、异常崩溃、网络抖动等原因卡在中间阶段时，
 * 定时任务会检测超时批次并重新触发对应阶段，确保最终一致性。
 *
 * <h3>检测逻辑</h3>
 * <pre>
 * 每 60 秒执行一次（可配置）：
 *
 * ① 检测 PENDING 超过 2 分钟未被触发 → 重触发 OCR 阶段
 * ② 检测 OCR_PROCESSING 超过 30 分钟 → 判断是否所有分片完成，推进到 OCR_DONE
 * ③ 检测 OCR_DONE / PARTIAL_FAIL 超时未进入分析 → 触发分析（按批次 {@code extra_info} 合并后 analysis 开启）
 * ④ ANALYZING 卡死需业务侧排查（此处未单独补偿）
 * ⑤ 检测 ANALYZED 超时未进入对比 → 触发对比（按批次合并后 compare 开启）
 * </pre>
 *
 * <p>通过配置 {@code eap.pipeline.scheduler.enabled=false} 可关闭定时任务（测试环境可用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "eap.pipeline.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OcrPipelineScheduler {

    private final EapPipelineProperties           pipelineProperties;
    private final PipelineEffectiveConfigResolver pipelineEffectiveConfigResolver;
    private final OcrPipelineBatchMapper          batchMapper;
    private final OcrPipelineService              pipelineService;

    /**
     * 主补偿入口，每 60 秒执行一次。
     * fixedDelay 保证上一次执行完成后才开始下次计时，避免并发堆积。
     */
    @Scheduled(fixedDelayString = "${eap.pipeline.scheduler.interval-ms:60000}")
    public void compensate() {
        log.debug("[PipelineScheduler] 补偿扫描开始");
        EapPipelineProperties.Scheduler sch = pipelineProperties.getScheduler();
        int pendingStale = sch.getPendingStaleSeconds();
        int doneStale = sch.getDoneStaleSeconds();

        // ① PENDING 超时 → 重触发 OCR
        retriggerPhase(OcrPipelineBatch.STATUS_PENDING, pendingStale, "OCR",
                b -> pipelineService.triggerOcrPhase(b.getBatchNo()));

        retriggerPhaseIf(OcrPipelineBatch.STATUS_OCR_DONE, doneStale, "Analysis",
                b -> pipelineEffectiveConfigResolver.resolve(b.getExtraInfo()).analysisEnabled(),
                b -> pipelineService.triggerAnalysisPhase(b.getBatchNo()));
        retriggerPhaseIf(OcrPipelineBatch.STATUS_PARTIAL_FAIL, doneStale, "Analysis(partial)",
                b -> pipelineEffectiveConfigResolver.resolve(b.getExtraInfo()).analysisEnabled(),
                b -> pipelineService.triggerAnalysisPhase(b.getBatchNo()));

        retriggerPhaseIf(OcrPipelineBatch.STATUS_ANALYZED, doneStale, "Compare",
                b -> pipelineEffectiveConfigResolver.resolve(b.getExtraInfo()).compareEnabled(),
                b -> pipelineService.triggerComparePhase(b.getBatchNo()));

        log.debug("[PipelineScheduler] 补偿扫描结束");
    }

    private void retriggerPhase(String status, int staleSeconds,
                                 String phaseName, java.util.function.Consumer<OcrPipelineBatch> action) {
        retriggerPhaseIf(status, staleSeconds, phaseName, b -> true, action);
    }

    private void retriggerPhaseIf(String status, int staleSeconds, String phaseName,
                                  Predicate<OcrPipelineBatch> shouldRun,
                                  java.util.function.Consumer<OcrPipelineBatch> action) {
        try {
            List<OcrPipelineBatch> stale = batchMapper.findStale(status, staleSeconds);
            if (stale.isEmpty()) return;
            log.info("[PipelineScheduler] 检测到 {} 个 {} 状态超时批次（>{} 秒），筛选后补偿 {} 阶段",
                    stale.size(), status, staleSeconds, phaseName);
            for (OcrPipelineBatch batch : stale) {
                try {
                    if (!shouldRun.test(batch)) {
                        log.debug("[PipelineScheduler] 跳过 batchNo={}（本 pipeline 未启用该阶段）", batch.getBatchNo());
                        continue;
                    }
                    log.info("[PipelineScheduler] 补偿触发 batchNo={}, phase={}", batch.getBatchNo(), phaseName);
                    batchMapper.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OcrPipelineBatch>()
                            .eq(OcrPipelineBatch::getId, batch.getId())
                            .set(OcrPipelineBatch::getTriggerSource, OcrPipelineBatch.SOURCE_SCHEDULER));
                    action.accept(batch);
                } catch (Exception e) {
                    log.error("[PipelineScheduler] 补偿触发失败 batchNo={}, phase={}, error={}",
                            batch.getBatchNo(), phaseName, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("[PipelineScheduler] 扫描 {} 状态批次失败: {}", status, e.getMessage(), e);
        }
    }
}
