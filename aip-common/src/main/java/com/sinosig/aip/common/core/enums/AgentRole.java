package com.sinosig.aip.common.core.enums;

public enum AgentRole {
    PLANNER("规划Agent - 拆解任务"),
    EXECUTOR("执行Agent - 工具调用"),
    REVIEWER("审查Agent - 质量评估"),
    COMMUNICATOR("通信Agent - 报告生成"),
    INTERACTION_CENTER("交互中心Agent - 意图识别与路由"),
    PROCUREMENT_AUDITOR("招采稽核智能体"),
    CLUE_DISCOVERY("线索发现智能体"),
    RISK_ANALYSIS("机构风险透视分析智能体"),
    MONITORING("监测预警智能体");

    private final String description;

    AgentRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
