package com.sinosig.aip.engine.rule;

import com.sinosig.aip.data.entity.ClueResult;

import java.util.List;

/**
 * 审计规则接口
 * 规则SQL引擎的核心抽象，每条规则对应一类合规风险场景。
 *
 * <pre>
 * 执行流程：统一数据仓库 → AuditRule.execute(orgCode) → 疑点线索结果
 * </pre>
 */
public interface AuditRule {

    /**
     * 规则名称（用于 clue_result.rule_name 记录）
     */
    String getRuleName();

    /**
     * 线索类型（UNTENDERED / SPLIT_PURCHASE / COLLUSIVE_BID / CONFLICT_OF_INTEREST）
     */
    String getClueType();

    /**
     * 默认风险等级（HIGH / MEDIUM / LOW）
     */
    String getRiskLevel();

    /**
     * 执行规则检测
     *
     * @param orgCode 机构编码
     * @return 命中规则的疑点线索列表（未写入数据库，由调用方统一持久化）
     */
    List<ClueResult> execute(String orgCode);
}
