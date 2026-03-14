package com.enterprise.agent.engine.agent.risk;

import com.enterprise.agent.common.ai.service.LlmService;
import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.core.agent.BaseAgent;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.engine.agent.risk.toolkit.RiskAnalysisToolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * RiskAnalysisAgent - 机构风险透视分析智能体
 * <p>
 * 对机构进行多维度风险画像，量化评分并自动生成综合风险报告，
 * 支撑稽核人员快速掌握机构整体风险全貌。
 */
@Slf4j
@Service
public class RiskAnalysisAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是专业的机构风险透视分析智能体，负责对机构进行全面风险画像。

            核心职责：
            1. 多维度风险评估（经营/合规/财务/采购四大维度）
            2. 风险量化评分（0-100分，分数越高风险越低）
            3. 主要条线风险分布分析
            4. 历史稽核情况汇总
            5. 生成机构综合风险报告

            评分标准：
            - 90-100：低风险（绿色）
            - 70-89：中低风险（黄色）
            - 50-69：中高风险（橙色）
            - 0-49：高风险（红色）
            """;

    private final RiskAnalysisToolkit toolkit;

    public RiskAnalysisAgent(LlmService llmService, ChatModel chatModel,
                              RiskAnalysisToolkit toolkit) {
        super(llmService, chatModel);
        this.toolkit = toolkit;
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.RISK_ANALYSIS;
    }

    @Override
    protected String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        logStart(context);
        try {
            String goal = sanitizeInput(context.getGoal());
            ChatClient client = buildChatClient()
                    .defaultTools(toolkit)
                    .build();

            String output = client.prompt()
                    .user(goal)
                    .call()
                    .content();

            AgentResult result = AgentResult.builder()
                    .agentRole(AgentRole.RISK_ANALYSIS)
                    .output(output)
                    .success(true)
                    .build();
            logEnd(context, result);
            return result;
        } catch (Exception e) {
            log.error("[RiskAnalysisAgent] 执行失败: {}", e.getMessage(), e);
            return AgentResult.failure(AgentRole.RISK_ANALYSIS, "机构风险透视分析执行失败：" + e.getMessage());
        }
    }

    /**
     * 对指定机构执行全面风险透视分析
     *
     * @param orgCode 机构编码
     */
    public AgentResult analyzeOrgRisk(String orgCode) {
        log.info("[RiskAnalysisAgent] 机构风险透视分析, orgCode={}", orgCode);
        String goal = String.format(
                "请对机构编码为【%s】的单位执行全面风险透视分析，完成以下步骤并生成综合报告：\n" +
                "1. 获取机构经营指标数据（保费收入、赔付率等）\n" +
                "2. 计算经营/合规/财务/采购四个维度的风险评分\n" +
                "3. 分析主要条线风险分布情况\n" +
                "4. 查询历史稽核情况及问题整改状态\n" +
                "5. 生成机构综合风险分析报告（含改进建议）",
                orgCode);
        AgentContext context = AgentContext.builder()
                .taskId(String.valueOf(System.currentTimeMillis()))
                .goal(goal)
                .build();
        return execute(context);
    }
}
