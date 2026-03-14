package com.enterprise.agent.business.screening.toolkit;

import com.enterprise.agent.data.entity.ProcurementBid;
import com.enterprise.agent.data.entity.ProcurementContract;
import com.enterprise.agent.data.entity.SupplierRelation;
import com.enterprise.agent.data.mapper.ProcurementBidMapper;
import com.enterprise.agent.data.mapper.ProcurementContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ProcurementAuditToolkit - 招采稽核专属技能集
 * <p>
 * 实现四个细分稽核场景：
 * 1. 大额采购未招标检测
 * 2. 化整为零识别
 * 3. 围标串标识别
 * 4. 利益输送预警
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcurementAuditToolkit {

    private final ProcurementContractMapper contractMapper;
    private final ProcurementBidMapper bidMapper;

    // ----------------------------------------------------------------
    // 场景1：大额采购未招标检测
    // ----------------------------------------------------------------

    @Tool(name = "检测大额采购未招标问题。对比费控合同与招采系统，识别超过金额门槛但无招采流程的采购项目。参数：orgCode（机构编码），thresholdWan（招采门槛万元，默认50）")
    public String detectUntendered(String orgCode, Double thresholdWan) {
        log.info("[ProcurementAuditToolkit] detectUntendered, orgCode={}, threshold={}万", orgCode, thresholdWan);
        double threshold = (thresholdWan == null || thresholdWan <= 0) ? 50.0 : thresholdWan;
        BigDecimal thresholdAmount = BigDecimal.valueOf(threshold * 10000);

        // 使用 Mock 数据（真实场景替换为数据库查询）
        List<ProcurementContract> allContracts = ProcurementContractMapper.getMockContracts();
        List<ProcurementContract> hits = allContracts.stream()
                .filter(c -> orgCode == null || orgCode.equals(c.getOrgCode()))
                .filter(c -> Boolean.FALSE.equals(c.getHasZcProcess()))
                .filter(c -> c.getContractAmount() != null
                        && c.getContractAmount().compareTo(thresholdAmount) >= 0)
                .sorted(Comparator.comparing(ProcurementContract::getContractAmount).reversed())
                .collect(Collectors.toList());

        if (hits.isEmpty()) {
            return String.format("【场景1：大额采购未招标】未发现超过%.0f万元且无招采流程的合同。", threshold);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【场景1：大额采购未招标】发现 %d 条疑似大额采购未招标记录（门槛：%.0f万元）：\n\n", hits.size(), threshold));
        for (int i = 0; i < hits.size(); i++) {
            ProcurementContract c = hits.get(i);
            sb.append(String.format("%d. 项目名称：%s\n   供应商：%s\n   合同金额：%.2f万元\n   合同日期：%s\n   是否有招采流程：否  ⚠️ 风险等级：%s\n\n",
                    i + 1,
                    c.getProjectName(),
                    c.getSupplierName(),
                    toWan(c.getContractAmount()),
                    c.getContractDate(),
                    c.getContractAmount().compareTo(BigDecimal.valueOf(500000)) >= 0 ? "高" : "中"));
        }
        sb.append("建议：对上述项目补充招采合规性说明，或启动事后整改程序。");
        return sb.toString();
    }

    // ----------------------------------------------------------------
    // 场景2：化整为零识别
    // ----------------------------------------------------------------

    @Tool(name = "识别化整为零拆分采购行为。统计短期内同一供应商同类项目多笔合同，累计金额接近招采门槛的视为疑似拆分。参数：orgCode，windowDays（时间窗口天数），thresholdWan（门槛万元）")
    public String detectSplitPurchase(String orgCode, Integer windowDays, Double thresholdWan) {
        log.info("[ProcurementAuditToolkit] detectSplitPurchase, orgCode={}, window={}天, threshold={}万",
                orgCode, windowDays, thresholdWan);
        int window = (windowDays == null || windowDays <= 0) ? 30 : windowDays;
        double threshold = (thresholdWan == null || thresholdWan <= 0) ? 50.0 : thresholdWan;
        BigDecimal thresholdAmount = BigDecimal.valueOf(threshold * 10000);
        LocalDate cutoff = LocalDate.now().minusDays(window);

        List<ProcurementContract> allContracts = ProcurementContractMapper.getMockContracts();
        List<ProcurementContract> recentContracts = allContracts.stream()
                .filter(c -> orgCode == null || orgCode.equals(c.getOrgCode()))
                .filter(c -> c.getContractDate() != null && !c.getContractDate().isBefore(cutoff))
                .collect(Collectors.toList());

        // 按供应商+项目类别分组，统计累计金额
        Map<String, List<ProcurementContract>> groups = recentContracts.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getSupplierId() + "||" + c.getProjectCategory()
                ));

        // 筛选累计金额在门槛 80%-130% 范围内且笔数 >= 2 的分组
        BigDecimal low = thresholdAmount.multiply(BigDecimal.valueOf(0.80));
        BigDecimal high = thresholdAmount.multiply(BigDecimal.valueOf(1.30));

        List<Map.Entry<String, List<ProcurementContract>>> suspiciousGroups = groups.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .filter(e -> {
                    BigDecimal total = e.getValue().stream()
                            .map(ProcurementContract::getContractAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return total.compareTo(low) >= 0 && total.compareTo(high) <= 0;
                })
                .collect(Collectors.toList());

        if (suspiciousGroups.isEmpty()) {
            return String.format("【场景2：化整为零】在%d天时间窗口内，未发现疑似化整为零拆分采购行为（门槛：%.0f万元）。",
                    window, threshold);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【场景2：化整为零】发现 %d 组疑似拆分采购行为（时间窗口：%d天，门槛：%.0f万元）：\n\n",
                suspiciousGroups.size(), window, threshold));

        for (int gi = 0; gi < suspiciousGroups.size(); gi++) {
            Map.Entry<String, List<ProcurementContract>> entry = suspiciousGroups.get(gi);
            List<ProcurementContract> contracts = entry.getValue();
            String supplierName = contracts.get(0).getSupplierName();
            String category = contracts.get(0).getProjectCategory();
            BigDecimal total = contracts.stream()
                    .map(ProcurementContract::getContractAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            sb.append(String.format("【疑似组%d】供应商：%s | 项目类别：%s | 合同数：%d笔 | 累计金额：%.2f万元  ⚠️ 风险等级：高\n",
                    gi + 1, supplierName, category, contracts.size(), toWan(total)));
            for (int i = 0; i < contracts.size(); i++) {
                ProcurementContract c = contracts.get(i);
                sb.append(String.format("  %d. %s  %s  %.2f万元\n",
                        i + 1, c.getContractDate(), c.getProjectName(), toWan(c.getContractAmount())));
            }
            sb.append("\n");
        }
        sb.append("建议：核查上述合同是否存在人为拆分意图，追溯审批链条，必要时合并评估是否需补招采程序。");
        return sb.toString();
    }

    // ----------------------------------------------------------------
    // 场景3：围标串标识别
    // ----------------------------------------------------------------

    @Tool(name = "识别围标串标行为。分析同一项目多家投标文件的相似度，超过阈值的视为疑似围标。同时检查投标单位股东是否存在关联关系。参数：bidProjectId（项目ID），similarityThreshold（相似度阈值0-1，默认0.85）")
    public String detectCollusiveBidding(String bidProjectId, Double similarityThreshold) {
        log.info("[ProcurementAuditToolkit] detectCollusiveBidding, projectId={}, threshold={}",
                bidProjectId, similarityThreshold);
        double threshold = (similarityThreshold == null || similarityThreshold <= 0) ? 0.85 : similarityThreshold;

        List<ProcurementBid> bids = ProcurementBidMapper.getMockBids().stream()
                .filter(b -> bidProjectId == null || bidProjectId.equals(b.getBidProjectId()))
                .collect(Collectors.toList());

        if (bids.size() < 2) {
            return String.format("【场景3：围标串标】项目 %s 的投标记录不足2条，无法进行相似度分析。", bidProjectId);
        }

        String projectName = bids.get(0).getProjectName();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【场景3：围标串标】项目「%s」（ID：%s）稽核结果：\n\n", projectName, bidProjectId));

        // 1. 两两计算投标文件相似度
        List<String[]> highSimilarPairs = new ArrayList<>();
        sb.append("▶ 投标文件相似度矩阵（Jaccard相似度）：\n");
        for (int i = 0; i < bids.size(); i++) {
            for (int j = i + 1; j < bids.size(); j++) {
                ProcurementBid bi = bids.get(i);
                ProcurementBid bj = bids.get(j);
                double sim = jaccardSimilarity(bi.getBidContent(), bj.getBidContent());
                String flag = sim >= threshold ? "  ⚠️ 高度相似" : "";
                sb.append(String.format("  %s vs %s：相似度 %.2f%s\n",
                        bi.getSupplierName(), bj.getSupplierName(), sim, flag));
                if (sim >= threshold) {
                    highSimilarPairs.add(new String[]{bi.getSupplierName(), bj.getSupplierName(),
                            String.format("%.2f", sim)});
                }
            }
        }

        // 2. 检查投标单位股东/法人是否重叠
        sb.append("\n▶ 股东/法人关联关系分析：\n");
        Map<String, Set<String>> supplierPersons = new HashMap<>();
        for (ProcurementBid bid : bids) {
            Set<String> persons = new HashSet<>();
            if (bid.getLegalPerson() != null) persons.add(bid.getLegalPerson());
            if (bid.getShareholders() != null) {
                String raw = bid.getShareholders().replaceAll("[\\[\\]\"\\s]", "");
                persons.addAll(Arrays.asList(raw.split(",")));
            }
            supplierPersons.put(bid.getSupplierName(), persons);
        }

        List<String> overlapResults = new ArrayList<>();
        List<String> supplierNames = new ArrayList<>(supplierPersons.keySet());
        for (int i = 0; i < supplierNames.size(); i++) {
            for (int j = i + 1; j < supplierNames.size(); j++) {
                String sA = supplierNames.get(i);
                String sB = supplierNames.get(j);
                Set<String> setA = new HashSet<>(supplierPersons.get(sA));
                Set<String> setB = supplierPersons.get(sB);
                setA.retainAll(setB);
                if (!setA.isEmpty()) {
                    String overlap = String.join("、", setA);
                    overlapResults.add(String.format("  %s 与 %s 存在共同关联人：%s  ⚠️", sA, sB, overlap));
                }
            }
        }
        if (overlapResults.isEmpty()) {
            sb.append("  未发现股东/法人重叠情况。\n");
        } else {
            overlapResults.forEach(r -> sb.append(r).append("\n"));
        }

        // 3. 综合判断
        sb.append("\n▶ 稽核结论：\n");
        if (!highSimilarPairs.isEmpty() || !overlapResults.isEmpty()) {
            sb.append(String.format("  ⚠️ 风险等级：高\n"));
            if (!highSimilarPairs.isEmpty()) {
                sb.append("  存在投标文件高度相似（≥").append(String.format("%.0f%%", threshold * 100)).append("），疑似使用同一模板串标。\n");
            }
            if (!overlapResults.isEmpty()) {
                sb.append("  存在投标单位股东/法人关联，疑似关联企业围标。\n");
            }
        } else {
            sb.append("  未发现明显围标串标迹象。\n");
        }
        sb.append("\n建议：移交纪检/法务部门复核，调取原始投标文件进行深度比对，必要时启动废标程序。");
        return sb.toString();
    }

    // ----------------------------------------------------------------
    // 场景4：利益输送预警
    // ----------------------------------------------------------------

    @Tool(name = "检测利益输送风险。比对中标供应商的股东、法人、董监高信息与公司内部员工名单，识别潜在关联关系和利益冲突。参数：orgCode（机构编码），supplierId（供应商ID，为空则全量排查）")
    public String detectConflictOfInterest(String orgCode, String supplierId) {
        log.info("[ProcurementAuditToolkit] detectConflictOfInterest, orgCode={}, supplierId={}", orgCode, supplierId);

        // 模拟内部员工名单（EHR系统数据）
        Map<String, String> internalEmployees = buildInternalEmployeeMap();

        // 获取供应商关联关系数据（模拟企查查数据）
        List<SupplierRelation> relations = ProcurementBidMapper.getMockSupplierRelations().stream()
                .filter(r -> supplierId == null || supplierId.isBlank() || supplierId.equals(r.getSupplierId()))
                .collect(Collectors.toList());

        // 比对：关联人员姓名是否出现在内部员工名单中
        List<Map<String, String>> conflicts = new ArrayList<>();
        for (SupplierRelation relation : relations) {
            String personName = relation.getRelatedPersonName();
            if (internalEmployees.containsKey(personName)) {
                Map<String, String> conflict = new LinkedHashMap<>();
                conflict.put("supplierName", relation.getSupplierName());
                conflict.put("supplierId", relation.getSupplierId());
                conflict.put("relatedPerson", personName);
                conflict.put("relationType", relation.getRelationType());
                conflict.put("shareRatio", relation.getShareRatio() != null
                        ? relation.getShareRatio() + "%" : "N/A");
                conflict.put("internalEmployeeId", internalEmployees.get(personName));
                conflict.put("internalRole", getEmployeeRole(internalEmployees.get(personName)));
                conflicts.add(conflict);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【场景4：利益输送预警】");
        if (supplierId != null && !supplierId.isBlank()) {
            sb.append(String.format("供应商（ID：%s）", supplierId));
        } else {
            sb.append("全量排查");
        }
        sb.append("稽核结果：\n\n");

        if (conflicts.isEmpty()) {
            sb.append("未发现供应商关联人员与内部员工存在同名关联关系。");
            return sb.toString();
        }

        sb.append(String.format("⚠️ 发现 %d 条疑似利益输送线索：\n\n", conflicts.size()));
        for (int i = 0; i < conflicts.size(); i++) {
            Map<String, String> c = conflicts.get(i);
            sb.append(String.format(
                    "线索%d  ⚠️ 风险等级：高\n" +
                    "  中标供应商：%s（ID：%s）\n" +
                    "  关联人员：%s（%s，持股：%s）\n" +
                    "  关联内部员工：%s（%s）\n\n",
                    i + 1,
                    c.get("supplierName"), c.get("supplierId"),
                    c.get("relatedPerson"), c.get("relationType"), c.get("shareRatio"),
                    c.get("internalEmployeeId"), c.get("internalRole")
            ));
        }
        sb.append("建议：立即启动利益冲突专项调查，涉事员工需进行回避声明核查，并上报合规委员会。");
        return sb.toString();
    }

    // ----------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------

    /**
     * 计算两段文本的 Jaccard 相似度（基于词集合）
     */
    private double jaccardSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        Set<String> set1 = new HashSet<>(Arrays.asList(text1.trim().split("\\s+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(text2.trim().split("\\s+")));
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    /**
     * 金额（元）转万元
     */
    private double toWan(BigDecimal amount) {
        if (amount == null) return 0.0;
        return amount.divide(BigDecimal.valueOf(10000), 2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 构建内部员工姓名→工号映射（模拟EHR系统数据）
     */
    private Map<String, String> buildInternalEmployeeMap() {
        Map<String, String> map = new HashMap<>();
        map.put("赵海波", "EMP-20231");  // 采购部经理
        map.put("林晓峰", "EMP-10087");  // 行政部副主任
        map.put("王建国", "EMP-30045");  // 信息技术部总监
        map.put("张立新", "EMP-40012");  // 财务部主任
        map.put("李慧敏", "EMP-50078");  // 合规部专员
        return map;
    }

    /**
     * 根据员工ID获取职位描述（模拟EHR数据）
     */
    private String getEmployeeRole(String employeeId) {
        Map<String, String> roles = new HashMap<>();
        roles.put("EMP-20231", "采购部经理");
        roles.put("EMP-10087", "行政部副主任");
        roles.put("EMP-30045", "信息技术部总监");
        roles.put("EMP-40012", "财务部主任");
        roles.put("EMP-50078", "合规部专员");
        return roles.getOrDefault(employeeId, "未知职位");
    }
}
