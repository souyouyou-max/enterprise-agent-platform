package com.sinosig.aip.business.auditing.clue;

import com.sinosig.aip.business.auditing.clue.toolkit.ClueDiscoveryToolkit;
import com.sinosig.aip.common.ai.service.LlmService;
import com.sinosig.aip.common.core.enums.AgentRole;
import com.sinosig.aip.core.agent.BaseAgent;
import com.sinosig.aip.core.context.AgentContext;
import com.sinosig.aip.core.context.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * ClueDiscoveryAgent - 线索发现智能体
 * <p>
 * 负责从多维度审计数据中识别疑点线索，按风险等级分类输出，
 * 并生成结构化分析报告，为稽核人员提供精准核查方向。
 */
@Slf4j
@Service
public class ClueDiscoveryAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是专业的稽核线索发现智能体，专注于从多维度数据中识别审计疑点。

            核心职责：
            1. 扫描各审计主题（采购/财务/合同）的异常数据
            2. 识别违规违法风险线索（超付、未招标、利益冲突等）
            3. 生成结构化疑点分析报告
            4. 按风险等级（高/中/低）对线索进行分类

            输出格式：
            - 线索摘要：发现X条疑点线索
            - 高风险线索：（详细列举）
            - 中风险线索：（详细列举）
            - 核查建议：（针对每条线索的具体核查方向）
            """;

    private final ClueDiscoveryToolkit toolkit;

    public ClueDiscoveryAgent(LlmService llmService, ChatModel chatModel,
                               ClueDiscoveryToolkit toolkit) {
        super(llmService, chatModel);
        this.toolkit = toolkit;
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.CLUE_DISCOVERY;
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
                    .agentRole(AgentRole.CLUE_DISCOVERY)
                    .output(output)
                    .success(true)
                    .build();
            logEnd(context, result);
            return result;
        } catch (Exception e) {
            log.error("[ClueDiscoveryAgent] 执行失败: {}", e.getMessage(), e);
            return AgentResult.failure(AgentRole.CLUE_DISCOVERY, "线索发现执行失败：" + e.getMessage());
        }
    }

    /**
     * 按审计主题扫描线索
     *
     * @param orgCode 机构编码
     * @param topic   审计主题：procurement（采购）/ finance（财务）/ contract（合同）
     */
    public AgentResult scanByTopic(String orgCode, String topic) {
        log.info("[ClueDiscoveryAgent] 按主题扫描线索, orgCode={}, topic={}", orgCode, topic);
        String goal = String.format(
                "请对机构【%s】执行%s主题的疑点线索扫描，识别异常数据并按风险等级输出线索清单和核查建议。",
                orgCode, topic);
        AgentContext context = AgentContext.builder()
                .taskId(String.valueOf(System.currentTimeMillis()))
                .goal(goal)
                .build();
        return execute(context);
    }

    /**
     * 全量扫描：对指定机构执行所有审计主题的线索发现
     *
     * @param orgCode 机构编码
     */
    public AgentResult scanAll(String orgCode) {
        log.info("[ClueDiscoveryAgent] 全量线索扫描, orgCode={}", orgCode);
        String goal = String.format(
                "请对机构编码为【%s】的单位执行全量疑点线索扫描，依次扫描以下审计主题并汇总结论：\n" +
                "1. 采购主题：超付、未招标、供应商集中度异常\n" +
                "2. 财务主题：费用异常、报销违规\n" +
                "3. 合同主题：先付后签、条款异常\n" +
                "最后生成线索发现分析报告，含高/中/低风险线索汇总和核查建议。",
                orgCode);
        AgentContext context = AgentContext.builder()
                .taskId(String.valueOf(System.currentTimeMillis()))
                .goal(goal)
                .build();
        return execute(context);
    }
}
