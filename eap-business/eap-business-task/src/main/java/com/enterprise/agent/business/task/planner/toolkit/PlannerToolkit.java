package com.enterprise.agent.business.task.planner.toolkit;

import com.enterprise.agent.common.ai.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * PlannerToolkit - 规划 Agent 专属技能集
 * <p>
 * 提供任务分解、复杂度评估、优先级排序三项核心规划能力，
 * 通过 Spring AI @Tool 注解供 ChatClient 自动注册给 LLM 调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerToolkit {

    private final LlmService llmService;

    /**
     * 将高层业务目标拆解为可执行子任务列表（JSON格式）
     */
    @Tool(description = "将用户的高层业务目标拆解为3-5个具体可执行的子任务，返回JSON数组，" +
            "每项包含sequence、description、toolName(getSalesData/getEmployeeInfo/queryCrmData/generateSqlQuery/classifyTextSemantics/img2Text/professionalQa/none)、toolParams字段")
    public String decomposeGoal(String goal) {
        log.info("[PlannerToolkit] decomposeGoal, goal={}", goal.substring(0, Math.min(50, goal.length())));
        String prompt = String.format("""
                用户目标：%s

                请将此目标拆解为3-5个具体可执行的子任务，严格输出JSON数组格式：
                [
                  {
                    "sequence": 1,
                    "description": "子任务描述",
                    "toolName": "工具名称或none",
                    "toolParams": "工具参数JSON字符串或空"
                  }
                ]
                可用工具：getSalesData（部门+季度销售数据）、getEmployeeInfo（员工信息）、
                         queryCrmData（CRM客户数据）、generateSqlQuery（自然语言转SQL）、
                         classifyTextSemantics（语义文本分类）、img2Text（图片识别转文本）、
                         professionalQa（专业知识问答）
                只输出JSON，不要包含其他文字。
                """, goal);
        return llmService.simpleChat(prompt);
    }

    /**
     * 评估任务复杂度（LOW/MEDIUM/HIGH），返回预计步骤数
     */
    @Tool(description = "评估业务目标的执行复杂度，返回JSON：{\"level\":\"LOW|MEDIUM|HIGH\",\"estimatedSteps\":3,\"reason\":\"说明\"}")
    public String estimateComplexity(String goal) {
        log.info("[PlannerToolkit] estimateComplexity, goal={}", goal.substring(0, Math.min(50, goal.length())));
        String prompt = String.format("""
                请评估以下业务目标的执行复杂度：
                目标：%s

                评估标准：
                - LOW：单一数据查询或简单分析，1-2步骤
                - MEDIUM：多维度数据分析，3-4步骤
                - HIGH：跨系统数据整合或复杂决策分析，5步骤以上

                严格输出JSON格式（不含其他文字）：
                {"level":"LOW|MEDIUM|HIGH","estimatedSteps":数字,"reason":"评估理由"}
                """, goal);
        return llmService.simpleChat(prompt);
    }

    /**
     * 对子任务列表按优先级排序，返回重新排序后的JSON数组
     */
    @Tool(description = "对子任务列表按执行优先级重新排序，输入taskListJson为子任务JSON数组字符串，" +
            "返回按优先级排序后的JSON数组（优先考虑数据依赖关系和业务价值）")
    public String prioritizeTasks(String taskListJson) {
        log.info("[PlannerToolkit] prioritizeTasks");
        String prompt = String.format("""
                请对以下子任务列表按执行优先级重新排序：
                %s

                排序原则：
                1. 数据获取类任务优先于分析类任务
                2. 高依赖性任务（被其他任务依赖）优先执行
                3. 业务价值高的任务提前

                返回重新排序后的JSON数组，更新sequence字段以反映新顺序，不要包含其他文字。
                """, taskListJson);
        return llmService.simpleChat(prompt);
    }
}
