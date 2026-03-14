package com.enterprise.agent.engine.agent.risk.toolkit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * RiskAnalysisToolkit - 机构风险透视分析专属技能集
 * <p>
 * 提供机构经营指标、多维风险评分、条线风险分布、
 * 历史稽核记录及综合风险报告生成等能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAnalysisToolkit {

    @Tool(name = "获取机构经营指标数据，包括保费收入、赔付率、综合成本率等核心指标。参数：orgCode（机构编码）")
    public String getOperationalMetrics(String orgCode) {
        log.info("[RiskAnalysisToolkit] getOperationalMetrics, orgCode={}", orgCode);
        return String.format(
                "{\"orgCode\":\"%s\",\"premiumIncome\":\"12.8亿\",\"lossRatio\":\"68%%\"," +
                "\"combinedRatio\":\"95%%\",\"yoyGrowth\":\"12%%\"}",
                orgCode);
    }

    @Tool(name = "计算机构多维风险评分，包括经营/合规/财务/采购四个维度。参数：orgCode（机构编码）")
    public String calculateRiskScore(String orgCode) {
        log.info("[RiskAnalysisToolkit] calculateRiskScore, orgCode={}", orgCode);
        return String.format(
                "{\"orgCode\":\"%s\",\"overallScore\":72,\"operationalScore\":80," +
                "\"complianceScore\":65,\"financialScore\":75,\"procurementScore\":68," +
                "\"riskLevel\":\"中低风险\"}",
                orgCode);
    }

    @Tool(name = "查询机构主要条线的风险分布情况。参数：orgCode（机构编码）")
    public String getRiskDistribution(String orgCode) {
        log.info("[RiskAnalysisToolkit] getRiskDistribution, orgCode={}", orgCode);
        return "采购条线：中高风险（3条未整改问题）；财务条线：低风险；合同条线：中低风险；人员条线：低风险";
    }

    @Tool(name = "查询机构历史稽核情况，包括历次稽核结论和问题整改状态。参数：orgCode（机构编码）")
    public String getHistoricalAuditRecords(String orgCode) {
        log.info("[RiskAnalysisToolkit] getHistoricalAuditRecords, orgCode={}", orgCode);
        return "2024年度稽核：发现问题8条，已整改6条，未整改2条（超期）；" +
               "2023年度稽核：发现问题5条，全部整改完成";
    }

    @Tool(name = "生成机构综合风险分析报告。参数：orgCode（机构编码），includeRecommendations（是否包含改进建议）")
    public String generateRiskReport(String orgCode, Boolean includeRecommendations) {
        log.info("[RiskAnalysisToolkit] generateRiskReport, orgCode={}, includeRecommendations={}", orgCode, includeRecommendations);
        String recommendations = Boolean.TRUE.equals(includeRecommendations)
                ? "\n\n## 改进建议\n1. 针对采购条线3条未整改问题，限期30天完成整改\n2. 加强合规培训，提升合规评分至75分以上\n3. 建立月度风险监测机制"
                : "";
        return String.format(
                "# %s 机构风险透视报告\n\n## 综合风险评分：72分（中低风险）\n\n" +
                "## 各维度评分\n" +
                "| 维度 | 评分 | 风险等级 |\n" +
                "|------|------|----------|\n" +
                "| 经营 | 80   | 中低风险 |\n" +
                "| 合规 | 65   | 中高风险 |\n" +
                "| 财务 | 75   | 中低风险 |\n" +
                "| 采购 | 68   | 中高风险 |\n\n" +
                "## 主要风险点\n" +
                "- 合规评分偏低，存在3条未整改历史问题\n" +
                "- 采购条线风险集中，需重点关注%s",
                orgCode, recommendations);
    }
}
