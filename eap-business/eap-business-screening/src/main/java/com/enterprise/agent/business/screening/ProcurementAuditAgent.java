package com.enterprise.agent.business.screening;

import com.enterprise.agent.common.ai.service.LlmService;
import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.core.agent.BaseAgent;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.business.screening.toolkit.ProcurementAuditToolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * ProcurementAuditAgent - 招采稽核智能体
 * <p>
 * 支持全量稽核（运行全部4个场景）和单场景稽核，
 * 由 LLM 自主决策调用哪些工具并汇总稽核结论。
 */
@Slf4j
@Service
public class ProcurementAuditAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是专业的招采稽核智能体，专注于识别采购违规行为。

            你具备以下稽核能力：
            1. 大额采购未招标检测：识别应招未招的采购项目
            2. 化整为零识别：发现拆分采购规避招标的行为
            3. 围标串标识别：分析投标文件相似度和投标单位关联关系
            4. 利益输送预警：比对供应商关联人员与内部员工

            稽核原则：
            - 客观分析数据，不主观臆断
            - 明确指出疑点依据和风险等级（高/中/低）
            - 给出具体的核查建议
            - 输出格式：稽核摘要 + 风险清单 + 核查建议
            """;

    private final ProcurementAuditToolkit toolkit;

    public ProcurementAuditAgent(LlmService llmService, ChatModel chatModel,
                                  ProcurementAuditToolkit toolkit) {
        super(llmService, chatModel);
        this.toolkit = toolkit;
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.PROCUREMENT_AUDITOR;
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
                    .agentRole(AgentRole.PROCUREMENT_AUDITOR)
                    .output(output)
                    .success(true)
                    .build();
            logEnd(context, result);
            return result;
        } catch (Exception e) {
            log.error("[ProcurementAuditAgent] 执行失败: {}", e.getMessage(), e);
            return AgentResult.failure(AgentRole.PROCUREMENT_AUDITOR, "招采稽核执行失败：" + e.getMessage());
        }
    }

    /**
     * 全量稽核：对指定机构执行全部4个稽核场景
     */
    public AgentResult auditAll(String orgCode) {
        log.info("[ProcurementAuditAgent] 启动全量稽核, orgCode={}", orgCode);
        String goal = String.format(
                "请对机构编码为【%s】的单位执行全量招采稽核，依次执行以下四个场景并汇总结论：\n" +
                "1. 检测大额采购未招标问题（门槛50万元）\n" +
                "2. 识别化整为零拆分采购行为（30天窗口，50万元门槛）\n" +
                "3. 识别项目 BID-PROJECT-001 的围标串标行为\n" +
                "4. 全量检测利益输送风险\n" +
                "最后输出：稽核总结报告，含总体风险评级和优先处置建议。",
                orgCode);

        AgentContext context = AgentContext.builder()
                .taskId("AUDIT-" + orgCode + "-" + System.currentTimeMillis())
                .goal(goal)
                .build();
        return execute(context);
    }

    /**
     * 单场景稽核
     *
     * @param orgCode 机构编码
     * @param scene   场景标识：untendered / split / collusive / conflict
     */
    public AgentResult auditScene(String orgCode, String scene) {
        log.info("[ProcurementAuditAgent] 单场景稽核, orgCode={}, scene={}", orgCode, scene);
        String goal = buildSceneGoal(orgCode, scene);
        AgentContext context = AgentContext.builder()
                .taskId("AUDIT-" + scene.toUpperCase() + "-" + System.currentTimeMillis())
                .goal(goal)
                .build();
        return execute(context);
    }

    private String buildSceneGoal(String orgCode, String scene) {
        return switch (scene.toLowerCase()) {
            case "untendered" -> String.format(
                    "请对机构【%s】执行大额采购未招标检测（门槛50万元），输出疑似违规清单和整改建议。", orgCode);
            case "split" -> String.format(
                    "请对机构【%s】执行化整为零识别分析（30天时间窗口，50万元门槛），输出疑似拆分采购清单。", orgCode);
            case "collusive" -> String.format(
                    "请对项目 BID-PROJECT-001 执行围标串标识别分析（相似度阈值0.85），输出疑似围标供应商组合。");
            case "conflict" -> String.format(
                    "请对机构【%s】执行全量利益输送风险排查，比对所有中标供应商关联人员与内部员工，输出风险清单。", orgCode);
            default -> String.format(
                    "请对机构【%s】执行招采稽核分析，场景标识：%s，并给出稽核结论。", orgCode, scene);
        };
    }
}
