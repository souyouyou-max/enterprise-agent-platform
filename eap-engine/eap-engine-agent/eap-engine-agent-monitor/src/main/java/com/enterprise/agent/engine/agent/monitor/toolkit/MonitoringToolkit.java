package com.enterprise.agent.engine.agent.monitor.toolkit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * MonitoringToolkit - 监测预警专属技能集
 * <p>
 * 提供风险阈值检查、监测看板数据、预警规则管理
 * 及动态预警通知生成等能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitoringToolkit {

    @Tool(name = "检查机构各项风险指标是否超过预设阈值，返回预警列表。参数：orgCode（机构编码）")
    public String checkRiskThresholds(String orgCode) {
        log.info("[MonitoringToolkit] checkRiskThresholds, orgCode={}", orgCode);
        return """
                预警检查结果（3项触发预警）：
                【红色预警】采购未招标率：当前15%，阈值5%，超出200%
                【橙色预警】供应商集中度：当前78%，阈值60%，超出30%
                【黄色预警】合同超付率：当前8%，阈值6%，超出33%
                """;
    }

    @Tool(name = "获取机构风险监测看板数据，用于可视化展示。参数：orgCode（机构编码）")
    public String getDashboardData(String orgCode) {
        log.info("[MonitoringToolkit] getDashboardData, orgCode={}", orgCode);
        return String.format(
                "{\"orgCode\":\"%s\",\"alerts\":{\"red\":1,\"orange\":1,\"yellow\":1,\"green\":5}," +
                "\"trend\":\"上升\",\"lastUpdated\":\"%s\"}",
                orgCode, java.time.LocalDateTime.now());
    }

    @Tool(name = "获取当前所有预警规则和阈值配置。参数：orgCode（机构编码），category（采购/财务/合同，可为空表示全部）")
    public String getAlertRules(String orgCode, String category) {
        log.info("[MonitoringToolkit] getAlertRules, orgCode={}, category={}", orgCode, category);
        return "预警规则共12条：采购类4条（未招标率阈值5%、供应商集中度阈值60%、" +
               "化整为零阈值3笔/30天、合同超付阈值6%）；财务类4条；合同类4条";
    }

    @Tool(name = "生成风险动态预警通知，包含预警详情和处置建议。参数：orgCode（机构编码），alertLevel（red/orange/yellow）")
    public String generateAlertNotification(String orgCode, String alertLevel) {
        log.info("[MonitoringToolkit] generateAlertNotification, orgCode={}, alertLevel={}", orgCode, alertLevel);
        String levelName = switch (alertLevel.toLowerCase()) {
            case "red" -> "红色";
            case "orange" -> "橙色";
            case "yellow" -> "黄色";
            default -> alertLevel.toUpperCase();
        };
        return String.format(
                "【%s预警】机构%s触发%s级预警，请相关负责人于24小时内核查处置。" +
                "主要问题：采购未招标率超标，建议立即暂停相关采购流程并启动专项核查。",
                levelName, orgCode, levelName);
    }
}
