package com.sinosig.aip.engine.rag.insight.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据洞察结果（NL2BI Pipeline 完整输出）
 */
@Data
@Builder
public class InsightResult {

    /** 原始问题 */
    private String question;

    /** LLM 生成的 SQL */
    private String generatedSql;

    /** SQL 执行结果（原始数据行） */
    private List<Map<String, Object>> rawData;

    /** LLM 生成的数据分析报告 */
    private String analysis;

    /** 图表类型建议（如 bar / line / pie），供前端渲染参考 */
    private String chartHint;

    /** 查询是否成功 */
    private boolean success;

    /** 失败原因（仅 success=false 时有值） */
    private String errorMessage;
}
