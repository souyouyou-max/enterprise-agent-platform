package com.enterprise.agent.data.service;

import com.enterprise.agent.data.adapter.DataSourceAdapter;
import com.enterprise.agent.data.entity.ClueResult;
import com.enterprise.agent.data.mapper.ClueResultMapper;
import com.enterprise.agent.data.rule.AuditRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 审计引擎服务
 *
 * <p>统一编排数据同步和规则执行两个阶段：
 * <pre>
 * 外部平台（Mock）→ DataSourceAdapter.syncData() → 统一数据仓库
 *                                                      ↓
 *                                              AuditRule.execute()
 *                                                      ↓
 *                                            clue_result（疑点结果表）
 * </pre>
 *
 * <p>所有实现了 {@link DataSourceAdapter} 和 {@link AuditRule} 接口的 Spring 组件会被
 * 自动注入（通过 List 注入机制），新增数据源或规则只需添加 @Component 即可自动生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEngineService {

    /** 所有数据源适配器（Spring自动注入所有 DataSourceAdapter 实现） */
    private final List<DataSourceAdapter> adapters;

    /** 所有审计规则（Spring自动注入所有 AuditRule 实现） */
    private final List<AuditRule> rules;

    private final ClueResultMapper clueResultMapper;

    /**
     * 执行全量数据同步（所有适配器）
     * 建议在规则执行前调用，确保数据仓库数据最新。
     */
    public void syncAllDataSources() {
        log.info("[审计引擎] 开始数据同步，共{}个数据源", adapters.size());
        for (DataSourceAdapter adapter : adapters) {
            try {
                adapter.syncData();
            } catch (Exception e) {
                log.error("[审计引擎] 数据源[{}]同步失败：{}", adapter.getSourceName(), e.getMessage(), e);
            }
        }
        log.info("[审计引擎] 数据同步完成");
    }

    /**
     * 对指定机构执行全量规则检测
     * <p>执行流程：
     * <ol>
     *   <li>清除该机构已有的 PENDING 疑点记录（避免重复累积）</li>
     *   <li>依次执行所有规则，收集疑点线索</li>
     *   <li>批量持久化到 clue_result 表</li>
     * </ol>
     *
     * @param orgCode 机构编码
     * @return 本次检测命中的疑点线索列表
     */
    @Transactional
    public List<ClueResult> runAllRules(String orgCode) {
        log.info("[审计引擎] 开始对机构[{}]执行规则检测，共{}条规则", orgCode, rules.size());

        // 清除旧的 PENDING 记录（已确认/已排除的保留）
        int deleted = clueResultMapper.deletePendingByOrgCode(orgCode);
        if (deleted > 0) {
            log.info("[审计引擎] 已清除机构[{}]旧PENDING记录{}条", orgCode, deleted);
        }

        List<ClueResult> allClues = new ArrayList<>();

        for (AuditRule rule : rules) {
            try {
                List<ClueResult> clues = rule.execute(orgCode);
                allClues.addAll(clues);
            } catch (Exception e) {
                log.error("[审计引擎] 规则[{}]执行异常：{}", rule.getRuleName(), e.getMessage(), e);
            }
        }

        // 批量写入疑点结果表
        for (ClueResult clue : allClues) {
            clueResultMapper.insert(clue);
        }

        log.info("[审计引擎] 机构[{}]规则检测完成，共发现{}条疑点线索", orgCode, allClues.size());
        return allClues;
    }

    /**
     * 完整审计流程：先同步数据，再执行规则
     *
     * @param orgCode 机构编码
     * @return 本次检测命中的疑点线索列表
     */
    public List<ClueResult> fullAudit(String orgCode) {
        log.info("[审计引擎] 启动机构[{}]完整审计流程（同步+检测）", orgCode);
        syncAllDataSources();
        return runAllRules(orgCode);
    }

    /**
     * 查询机构待处理疑点线索
     *
     * @param orgCode 机构编码
     * @return PENDING 状态的疑点列表
     */
    public List<ClueResult> getPendingClues(String orgCode) {
        return clueResultMapper.findPendingByOrgCode(orgCode);
    }

    /**
     * 查询机构全部疑点线索（含已处理）
     *
     * @param orgCode 机构编码
     * @return 全部疑点列表
     */
    public List<ClueResult> getAllClues(String orgCode) {
        return clueResultMapper.findByOrgCode(orgCode);
    }
}
