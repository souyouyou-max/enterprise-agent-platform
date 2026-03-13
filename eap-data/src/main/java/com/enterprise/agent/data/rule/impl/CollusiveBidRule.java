package com.enterprise.agent.data.rule.impl;

import com.enterprise.agent.data.entity.ClueResult;
import com.enterprise.agent.data.mapper.ClueQueryMapper;
import com.enterprise.agent.data.rule.AuditRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 围标串标规则（CollusiveBidRule）
 *
 * <p><b>规则逻辑：</b>
 * 从招标投标记录（procurement_bid）中检测同一项目存在多个投标供应商，
 * 且这些供应商拥有相同法定代表人（通过供应商工商信息 supplier_info 比对），
 * 判定为"围标串标"嫌疑。
 *
 * <p><b>检测原理：</b>
 * 围标通常表现为：同一控制人（法定代表人）控制多家公司参与同一项目投标，
 * 通过安排一家报低价中标、其他公司故意报高价陪标的方式操纵招标结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollusiveBidRule implements AuditRule {

    private final ClueQueryMapper clueQueryMapper;

    @Override
    public String getRuleName() {
        return "围标串标规则";
    }

    @Override
    public String getClueType() {
        return "COLLUSIVE_BID";
    }

    @Override
    public String getRiskLevel() {
        return "HIGH";
    }

    @Override
    public List<ClueResult> execute(String orgCode) {
        log.info("[规则引擎] 执行规则：{}，机构：{}", getRuleName(), orgCode);

        List<Map<String, Object>> hits = clueQueryMapper.findCollusiveBids(orgCode);
        List<ClueResult> results = new ArrayList<>();

        for (Map<String, Object> row : hits) {
            String projectId = str(row, "bid_project_id");
            String projectName = str(row, "project_name");
            String winnerSupplier = str(row, "winner_supplier");
            String colluderSupplier = str(row, "colluder_supplier");
            String sharedLegalPerson = str(row, "shared_legal_person");
            BigDecimal winningAmount = toBigDecimal(row, "winning_amount");

            ClueResult clue = new ClueResult();
            clue.setOrgCode(orgCode);
            clue.setClueType(getClueType());
            clue.setRiskLevel(getRiskLevel());
            clue.setClueTitle(String.format("疑似围标串标：%s（项目：%s）", winnerSupplier, projectName));
            clue.setClueDetail(String.format(
                    "在项目【%s】（项目编号：%s）的招标过程中，" +
                    "中标供应商【%s】与投标供应商【%s】的法定代表人均为【%s】，" +
                    "两家公司由同一实际控制人控制，存在安排围标、串通投标的重大嫌疑。" +
                    "中标金额：%.2f 元。",
                    projectName, projectId,
                    winnerSupplier, colluderSupplier, sharedLegalPerson,
                    winningAmount));
            clue.setRelatedAmount(winningAmount);
            clue.setRelatedSupplier(winnerSupplier + "，" + colluderSupplier);
            clue.setRuleName(getRuleName());
            clue.setStatus("PENDING");
            clue.setCreatedAt(LocalDateTime.now());

            results.add(clue);
            log.warn("[{}] 命中：项目={}，中标方={}，陪标方={}，共同法人={}",
                    getRuleName(), projectName, winnerSupplier, colluderSupplier, sharedLegalPerson);
        }

        log.info("[规则引擎] 规则{}执行完成，命中{}条疑点", getRuleName(), results.size());
        return results;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
    }

    private BigDecimal toBigDecimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }
}
