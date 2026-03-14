package com.enterprise.agent.engine.agent.monitor;

import com.enterprise.agent.common.ai.service.LlmService;
import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.core.agent.BaseAgent;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.engine.agent.monitor.toolkit.MonitoringToolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * MonitoringAgent - 监测预警智能体
 * <p>
 * 实时监控关键风险指标，对超阈值指标进行分级预警，
 * 并推送动态预警通知，辅助稽核人员及时响应风险。
 */
@Slf4j
@Service
public class MonitoringAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是专业的监测预警智能体，负责实时监控风险指标并触发预警。

            核心职责：
            1. 监控关键风险指标是否超过阈值
            2. 对风险进行量化评分和等级分层
            3. 生成可视化预警看板数据
            4. 管理预警规则和阈值配置
            5. 推送动态风险预警通知

            预警级别：
            - 红色预警：立即处置（风险指标超阈值50%以上）
            - 橙色预警：重点关注（超阈值20-50%）
            - 黄色预警：一般关注（超阈值0-20%）
            - 绿色正常：指标正常
            """;

    private final MonitoringToolkit toolkit;

    public MonitoringAgent(LlmService llmService, ChatModel chatModel,
                            MonitoringToolkit toolkit) {
        super(llmService, chatModel);
        this.toolkit = toolkit;
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.MONITORING;
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
                    .agentRole(AgentRole.MONITORING)
                    .output(output)
                    .success(true)
                    .build();
            logEnd(context, result);
            return result;
        } catch (Exception e) {
            log.error("[MonitoringAgent] 执行失败: {}", e.getMessage(), e);
            return AgentResult.failure(AgentRole.MONITORING, "监测预警执行失败：" + e.getMessage());
        }
    }

    /**
     * 对指定机构执行全面监测预警检查
     *
     * @param orgCode 机构编码
     */
    public AgentResult monitorOrg(String orgCode) {
        log.info("[MonitoringAgent] 机构监测预警, orgCode={}", orgCode);
        String goal = String.format(
                "请对机构编码为【%s】的单位执行全面监测预警检查，完成以下步骤：\n" +
                "1. 检查所有风险指标是否超过预设阈值，列出触发预警的指标\n" +
                "2. 获取风险监测看板数据，分析整体趋势\n" +
                "3. 查看当前预警规则配置\n" +
                "4. 对最高级别预警生成处置通知\n" +
                "最终输出：预警汇总报告，含各级预警数量、重点关注指标和处置建议。",
                orgCode);
        AgentContext context = AgentContext.builder()
                .taskId(System.currentTimeMillis())
                .goal(goal)
                .build();
        return execute(context);
    }

    /**
     * 检查机构风险指标阈值并返回触发预警列表
     *
     * @param orgCode 机构编码
     */
    public AgentResult checkThresholds(String orgCode) {
        log.info("[MonitoringAgent] 阈值检查, orgCode={}", orgCode);
        String goal = String.format(
                "请对机构【%s】执行风险指标阈值检查，列出所有超阈值指标及其预警级别。",
                orgCode);
        AgentContext context = AgentContext.builder()
                .taskId(System.currentTimeMillis())
                .goal(goal)
                .build();
        return execute(context);
    }
}
