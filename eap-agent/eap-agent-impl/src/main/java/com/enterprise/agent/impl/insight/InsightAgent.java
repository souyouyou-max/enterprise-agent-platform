package com.enterprise.agent.impl.insight;

import com.enterprise.agent.insight.model.InsightResult;
import com.enterprise.agent.insight.service.DataQueryService;
import com.enterprise.agent.insight.service.InsightAnalysisService;
import com.enterprise.agent.insight.service.NlToSqlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 智能数据洞察 Agent（NL2BI 三步流水线）
 * <p>
 * investigate(question):
 *   Step 1 - NL2SQL：调用 LLM 将自然语言转为 SQL
 *   Step 2 - 执行：DataQueryService 安全执行 SELECT
 *   Step 3 - 分析：InsightAnalysisService 生成洞察报告
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightAgent {

    private final NlToSqlService nlToSqlService;
    private final DataQueryService dataQueryService;
    private final InsightAnalysisService insightAnalysisService;

    /**
     * 完整洞察流程：自然语言 → SQL → 执行 → 分析报告
     *
     * @param question 自然语言业务问题
     * @return 完整洞察结果
     */
    public InsightResult investigate(String question) {
        log.info("[InsightAgent] 开始洞察: {}", question);

        // Step 1: NL2SQL
        String sql;
        try {
            sql = nlToSqlService.generateSql(question, null);
        } catch (Exception e) {
            log.error("[InsightAgent] NL2SQL 失败", e);
            return InsightResult.builder()
                    .question(question)
                    .success(false)
                    .errorMessage("SQL 生成失败: " + e.getMessage())
                    .build();
        }

        // Step 2: 执行查询
        List<Map<String, Object>> rawData;
        try {
            rawData = dataQueryService.executeQuery(sql);
        } catch (Exception e) {
            log.error("[InsightAgent] SQL 执行失败: {}", sql, e);
            return InsightResult.builder()
                    .question(question)
                    .generatedSql(sql)
                    .success(false)
                    .errorMessage("SQL 执行失败: " + e.getMessage())
                    .build();
        }

        // Step 3: LLM 分析
        String analysis = insightAnalysisService.analyze(question, rawData);
        String chartHint = insightAnalysisService.extractChartHint(analysis);

        log.info("[InsightAgent] 洞察完成，数据行数: {}", rawData.size());
        return InsightResult.builder()
                .question(question)
                .generatedSql(sql)
                .rawData(rawData)
                .analysis(analysis)
                .chartHint(chartHint)
                .success(true)
                .build();
    }
}
