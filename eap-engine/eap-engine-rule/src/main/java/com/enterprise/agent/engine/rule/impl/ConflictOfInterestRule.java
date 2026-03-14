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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 利益冲突规则（ConflictOfInterestRule）
 *
 * <p><b>规则逻辑：</b>
 * 将招标供应商工商信息（supplier_info）与内部员工信息（internal_employee）进行交叉比对：
 * <ul>
 *   <li>场景A：供应商法定代表人与本机构内部员工同名 → 高度疑似利益输送</li>
 *   <li>场景B：供应商股东（JSON字段模糊匹配）与内部员工同名 → 疑似隐性利益冲突</li>
 * </ul>
 *
 * <p><b>检测原理：</b>
 * 内部员工以控股或实际控制的供应商参与本机构招标，属于重大利益冲突，
 * 违反企业采购廉洁规定，可能涉及利益输送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConflictOfInterestRule implements AuditRule {

    private final ClueQueryMapper clueQueryMapper;

    @Override
    public String getRuleName() {
        return "利益冲突规则";
    }

    @Override
    public String getClueType() {
        return "CONFLICT_OF_INTEREST";
    }

    @Override
    public String getRiskLevel() {
        return "HIGH";
    }

    @Override
    public List<ClueResult> execute(String orgCode) {
        log.info("[规则引擎] 执行规则：{}，机构：{}", getRuleName(), orgCode);

        List<ClueResult> results = new ArrayList<>();
        Set<String> deduplicateKeys = new HashSet<>();

        // 场景A：供应商法定代表人与内部员工同名
        List<Map<String, Object>> legalConflicts = clueQueryMapper.findLegalPersonConflicts(orgCode);
        for (Map<String, Object> row : legalConflicts) {
            String key = str(row, "supplier_id") + ":" + str(row, "employee_name") + ":legal";
            if (!deduplicateKeys.add(key)) continue;

            String supplierName = str(row, "supplier_name");
            String legalPerson = str(row, "legal_person");
            String employeeName = str(row, "employee_name");
            String department = str(row, "department");
            String position = str(row, "position");
            String projectName = str(row, "project_name");
            BigDecimal amount = toBigDecimal(row, "contract_amount");

            ClueResult clue = new ClueResult();
            clue.setOrgCode(orgCode);
            clue.setClueType(getClueType());
            clue.setRiskLevel(getRiskLevel());
            clue.setClueTitle(String.format("利益冲突（法人）：供应商%s法人与员工%s同名",
                    supplierName, employeeName));
            clue.setClueDetail(String.format(
                    "供应商【%s】的法定代表人为【%s】，与本机构内部员工【%s】（%s-%s）姓名完全一致。" +
                    "该供应商中标项目【%s】，合同金额 %.2f 元。" +
                    "内部员工以所控制的公司参与本机构招标，存在严重利益冲突，疑似利益输送。",
                    supplierName, legalPerson, employeeName, department, position,
                    projectName, amount));
            clue.setRelatedAmount(amount);
            clue.setRelatedSupplier(supplierName);
            clue.setRuleName(getRuleName());
            clue.setStatus("PENDING");
            clue.setCreatedAt(LocalDateTime.now());

            results.add(clue);
            log.warn("[{}] 命中（法人）：供应商={}，法人={}，同名员工={}（{}）",
                    getRuleName(), supplierName, legalPerson, employeeName, department);
        }

        // 场景B：供应商股东与内部员工同名
        List<Map<String, Object>> shareholderConflicts = clueQueryMapper.findShareholderConflicts(orgCode);
        for (Map<String, Object> row : shareholderConflicts) {
            String key = str(row, "supplier_id") + ":" + str(row, "employee_name") + ":shareholder";
            if (!deduplicateKeys.add(key)) continue;

            String supplierName = str(row, "supplier_name");
            String shareholders = str(row, "shareholders");
            String employeeName = str(row, "employee_name");
            String department = str(row, "department");
            String position = str(row, "position");
            String projectName = str(row, "project_name");
            BigDecimal amount = toBigDecimal(row, "contract_amount");

            ClueResult clue = new ClueResult();
            clue.setOrgCode(orgCode);
            clue.setClueType(getClueType());
            clue.setRiskLevel("MEDIUM"); // 股东与法人相比风险略低
            clue.setClueTitle(String.format("利益冲突（股东）：供应商%s股东与员工%s同名",
                    supplierName, employeeName));
            clue.setClueDetail(String.format(
                    "供应商【%s】的股东信息中包含姓名【%s】，与本机构内部员工【%s】（%s-%s）姓名一致。" +
                    "股东详情：%s。" +
                    "该供应商中标项目【%s】，合同金额 %.2f 元。" +
                    "内部员工以股东身份参与供应商决策，存在利益冲突风险。",
                    supplierName, employeeName, employeeName, department, position,
                    shareholders, projectName, amount));
            clue.setRelatedAmount(amount);
            clue.setRelatedSupplier(supplierName);
            clue.setRuleName(getRuleName());
            clue.setStatus("PENDING");
            clue.setCreatedAt(LocalDateTime.now());

            results.add(clue);
            log.warn("[{}] 命中（股东）：供应商={}，同名员工={}（{}），股东信息={}",
                    getRuleName(), supplierName, employeeName, department, shareholders);
        }

        log.info("[规则引擎] 规则{}执行完成，命中{}条疑点（法人{}条+股东{}条）",
                getRuleName(), results.size(), legalConflicts.size(), shareholderConflicts.size());
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
