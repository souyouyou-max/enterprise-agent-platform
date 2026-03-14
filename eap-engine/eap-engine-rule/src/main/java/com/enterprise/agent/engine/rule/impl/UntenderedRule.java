package com.enterprise.agent.engine.rule.impl;

import com.enterprise.agent.data.entity.ClueResult;
import com.enterprise.agent.data.mapper.ClueQueryMapper;
import com.enterprise.agent.engine.rule.AuditRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 大额未招标规则（UntenderedRule）
 *
 * <p><b>规则逻辑：</b>
 * 从付款台账（payment_record）中查找付款金额超过50万阈值，
 * 且无法在招标项目表（procurement_project）中找到对应招标记录的付款，
 * 判定为"大额采购未履行招标程序"违规。
 *
 * <p><b>关联逻辑：</b>
 * payment_record.contract_no = procurement_project.project_code
 * 通过左关联（LEFT JOIN），pp.id IS NULL 筛选出无招标记录的付款。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UntenderedRule implements AuditRule {

    /** 触发门槛：单笔付款金额超过50万 */
    private static final BigDecimal THRESHOLD = new BigDecimal("500000.00");

    private final ClueQueryMapper clueQueryMapper;

    @Override
    public String getRuleName() {
        return "大额未招标规则";
    }

    @Override
    public String getClueType() {
        return "UNTENDERED";
    }

    @Override
    public String getRiskLevel() {
        return "HIGH";
    }

    @Override
    public List<ClueResult> execute(String orgCode) {
        log.info("[规则引擎] 执行规则：{}，机构：{}", getRuleName(), orgCode);

        List<Map<String, Object>> hits = clueQueryMapper.findUntenderedPayments(orgCode, THRESHOLD);
        List<ClueResult> results = new ArrayList<>();

        for (Map<String, Object> row : hits) {
            String supplierName = str(row, "supplier_name");
            BigDecimal amount = toBigDecimal(row, "payment_amount");
            String contractNo = str(row, "contract_no");
            String category = str(row, "project_category");
            String purpose = str(row, "payment_purpose");

            ClueResult clue = new ClueResult();
            clue.setOrgCode(orgCode);
            clue.setClueType(getClueType());
            clue.setRiskLevel(getRiskLevel());
            clue.setClueTitle(String.format("大额采购未招标：%s（合同号：%s）", supplierName, contractNo));
            clue.setClueDetail(String.format(
                    "付款台账显示，向供应商【%s】支付%s类款项 %.2f 元（合同号：%s，用途：%s），" +
                    "但在招采系统中未发现对应的招标项目记录（project_code=%s），" +
                    "超过50万元招标门槛而未履行招标程序，疑似违规直接采购。",
                    supplierName, category, amount, contractNo, purpose, contractNo));
            clue.setRelatedAmount(amount);
            clue.setRelatedSupplier(supplierName);
            clue.setRuleName(getRuleName());
            clue.setStatus("PENDING");
            clue.setCreatedAt(LocalDateTime.now());

            results.add(clue);
            log.warn("[{}] 命中：供应商={}，金额={}，合同号={}",
                    getRuleName(), supplierName, amount, contractNo);
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
