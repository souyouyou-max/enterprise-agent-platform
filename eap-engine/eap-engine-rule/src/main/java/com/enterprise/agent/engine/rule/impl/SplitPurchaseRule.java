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
 * 化整为零规则（SplitPurchaseRule）
 *
 * <p><b>规则逻辑：</b>
 * 查找同一供应商在60天内存在多笔小额付款（每笔低于50万门槛），
 * 但累计金额超过50万招标门槛的情况，判定为"化整为零规避招标"。
 *
 * <p><b>SQL逻辑：</b>
 * GROUP BY supplier_id，HAVING COUNT >= 2 AND SUM > 500000 AND 每笔 < 500000
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SplitPurchaseRule implements AuditRule {

    /** 单笔上限：每笔付款金额须低于此阈值才认定为"拆分" */
    private static final BigDecimal SINGLE_THRESHOLD = new BigDecimal("500000.00");

    /** 累计下限：60天内同一供应商累计付款超过此金额触发规则 */
    private static final BigDecimal TOTAL_THRESHOLD = new BigDecimal("500000.00");

    private final ClueQueryMapper clueQueryMapper;

    @Override
    public String getRuleName() {
        return "化整为零规则";
    }

    @Override
    public String getClueType() {
        return "SPLIT_PURCHASE";
    }

    @Override
    public String getRiskLevel() {
        return "HIGH";
    }

    @Override
    public List<ClueResult> execute(String orgCode) {
        log.info("[规则引擎] 执行规则：{}，机构：{}", getRuleName(), orgCode);

        List<Map<String, Object>> hits = clueQueryMapper.findSplitPurchases(
                orgCode, SINGLE_THRESHOLD, TOTAL_THRESHOLD);
        List<ClueResult> results = new ArrayList<>();

        for (Map<String, Object> row : hits) {
            String supplierName = str(row, "supplier_name");
            String supplierId = str(row, "supplier_id");
            BigDecimal totalAmount = toBigDecimal(row, "total_amount");
            long contractCount = toLong(row, "contract_count");
            String earliestDate = str(row, "earliest_date");
            String latestDate = str(row, "latest_date");

            ClueResult clue = new ClueResult();
            clue.setOrgCode(orgCode);
            clue.setClueType(getClueType());
            clue.setRiskLevel(getRiskLevel());
            clue.setClueTitle(String.format("疑似化整为零规避招标：%s，60天内%d笔合计%.2f元",
                    supplierName, contractCount, totalAmount));
            clue.setClueDetail(String.format(
                    "供应商【%s】（%s）在60天内（%s 至 %s）共有 %d 笔付款记录，" +
                    "每笔均低于50万元招标门槛，但累计金额达 %.2f 元，超过招标门槛。" +
                    "存在将大额采购拆分为多笔小额合同以规避招标程序的嫌疑。",
                    supplierName, supplierId, earliestDate, latestDate,
                    contractCount, totalAmount));
            clue.setRelatedAmount(totalAmount);
            clue.setRelatedSupplier(supplierName);
            clue.setRuleName(getRuleName());
            clue.setStatus("PENDING");
            clue.setCreatedAt(LocalDateTime.now());

            results.add(clue);
            log.warn("[{}] 命中：供应商={}，笔数={}，合计金额={}",
                    getRuleName(), supplierName, contractCount, totalAmount);
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

    private long toLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }
}
