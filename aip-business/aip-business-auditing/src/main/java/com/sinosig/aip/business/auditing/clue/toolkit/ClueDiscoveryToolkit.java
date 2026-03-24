package com.sinosig.aip.business.auditing.clue.toolkit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * ClueDiscoveryToolkit - 线索发现专属技能集
 * <p>
 * 覆盖采购/财务/合同三大审计主题的疑点线索扫描，
 * 并提供结构化分析报告生成能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClueDiscoveryToolkit {

    @Tool(name = "扫描采购审计主题的疑点线索，包括超付、未招标、供应商集中度异常等。参数：orgCode（机构编码）")
    public String scanProcurementClues(String orgCode) {
        log.info("[ClueDiscoveryToolkit] scanProcurementClues, orgCode={}", orgCode);
        return """
                发现采购疑点线索3条：
                【高风险】供应商A与内部员工张某存在关联关系，涉及合同金额320万元
                【高风险】项目"XX系统建设"合同金额180万元，无招采流程记录
                【中风险】供应商B在30天内承接3笔同类合同，累计金额48万元（接近50万招标门槛）
                """;
    }

    @Tool(name = "扫描财务审计主题的疑点线索，包括费用异常、报销违规等。参数：orgCode（机构编码）")
    public String scanFinanceClues(String orgCode) {
        log.info("[ClueDiscoveryToolkit] scanFinanceClues, orgCode={}", orgCode);
        return "发现财务疑点线索2条：【中风险】部门A差旅费同比增长180%，异常波动；【低风险】员工B连续3个月在月末集中报销";
    }

    @Tool(name = "扫描合同审计主题的疑点线索，包括先付后签、条款异常等。参数：orgCode（机构编码）")
    public String scanContractClues(String orgCode) {
        log.info("[ClueDiscoveryToolkit] scanContractClues, orgCode={}", orgCode);
        return "发现合同疑点线索1条：【高风险】合同C付款日期（2024-03-01）早于签订日期（2024-03-15），存在先付后签异常";
    }

    @Tool(name = "生成线索发现分析报告，汇总所有审计主题的疑点线索。参数：orgCode（机构编码），cluesJson（线索数据JSON）")
    public String generateClueReport(String orgCode, String cluesJson) {
        log.info("[ClueDiscoveryToolkit] generateClueReport, orgCode={}", orgCode);
        return String.format(
                "# 线索发现分析报告\n机构：%s\n生成时间：%s\n\n## 高风险线索（2条）\n" +
                "- 供应商与内部员工存在关联关系，合同金额320万元\n" +
                "- 合同先付后签，付款日早于签约日14天\n\n" +
                "## 中风险线索（1条）\n" +
                "- 供应商集中承接同类合同，疑似化整为零规避招标\n\n" +
                "## 核查建议\n" +
                "1. 对高风险线索立即启动专项核查，暂停相关付款\n" +
                "2. 调取关联关系人员档案，核实利益冲突情况\n" +
                "3. 要求采购部门补充先付后签合同的审批说明",
                orgCode, java.time.LocalDate.now());
    }
}
