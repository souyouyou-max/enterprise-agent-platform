package com.enterprise.agent.business.task.executor.toolkit;

import com.enterprise.agent.tools.impl.CrmTool;
import com.enterprise.agent.tools.impl.EmployeeTool;
import com.enterprise.agent.tools.impl.SalesDataTool;
import com.enterprise.agent.tools.impl.ZhengyanPlatformTool;
import com.enterprise.agent.tools.impl.SqlGeneratorTool;
import com.enterprise.agent.tools.impl.ZhengyanTextClassificationTool;
import com.enterprise.agent.tools.impl.DazhiOcrTool;
import com.enterprise.agent.common.core.response.ToolResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ExecutorToolkit - 执行 Agent 专属技能集
 * <p>
 * 整合企业数据工具，每个 @Tool 方法委托给对应的 EnterpriseTool 实现类。
 * LLM 可根据子任务需求自主选择并调用相应工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorToolkit {

    private final SalesDataTool salesDataTool;
    private final EmployeeTool employeeTool;
    private final CrmTool crmTool;
    private final SqlGeneratorTool sqlGeneratorTool;
    private final ZhengyanTextClassificationTool zhengyanTextClassificationTool;
    private final ZhengyanPlatformTool zhengyanPlatformTool;
    private final DazhiOcrTool dazhiOcrTool;
    private final ObjectMapper objectMapper;

    /**
     * 查询指定部门的季度销售数据
     */
    @Tool(description = "查询企业部门季度销售数据，返回营收、订单数、增长率等指标。" +
            "department可选值：all（全部）、华南区、华北区、华东区、西部区；" +
            "quarter格式示例：Q4-2024")
    public String getSalesData(String department, String quarter) {
        log.info("[ExecutorToolkit] getSalesData, dept={}, quarter={}", department, quarter);
        String params = String.format("{\"department\":\"%s\",\"quarter\":\"%s\"}", department, quarter);
        return salesDataTool.execute(params).toJsonString();
    }

    /**
     * 查询员工基本信息
     */
    @Tool(description = "根据员工ID查询员工基本信息（姓名、部门、职位、邮箱、司龄、级别）。" +
            "employeeId格式示例：E001")
    public String getEmployeeInfo(String employeeId) {
        log.info("[ExecutorToolkit] getEmployeeInfo, id={}", employeeId);
        String params = String.format("{\"employeeId\":\"%s\"}", employeeId);
        return employeeTool.execute(params).toJsonString();
    }

    /**
     * 查询客户CRM数据
     */
    @Tool(description = "根据客户ID查询CRM客户数据，包括企业信息、合同金额、续约概率、满意度评分和近期订单。" +
            "customerId格式示例：C001")
    public String queryCrmData(String customerId) {
        log.info("[ExecutorToolkit] queryCrmData, customerId={}", customerId);
        String params = String.format("{\"customerId\":\"%s\"}", customerId);
        return crmTool.execute(params).toJsonString();
    }

    /**
     * 自然语言转SQL查询
     */
    @Tool(description = "将自然语言问题转换为PostgreSQL查询语句，返回包含sql和explanation字段的JSON。" +
            "仅支持SELECT查询，不支持DML/DDL操作。")
    public String generateSqlQuery(String question) {
        log.info("[ExecutorToolkit] generateSqlQuery, question={}", question.substring(0, Math.min(50, question.length())));
        String params = String.format("{\"question\":\"%s\"}", question.replace("\"", "\\\""));
        return sqlGeneratorTool.execute(params).toJsonString();
    }

    /**
     * 正言语义文本分类
     */
    @Tool(description = "调用正言语义文本分类接口，对文本进行标签分类。" +
            "text为待分类文本；labels为候选标签数组（可选）；topK与threshold可选。")
    public String classifyTextSemantics(String text, String labelsJson, Integer topK, Double threshold) {
        String safeText = text == null ? "" : text;
        log.info("[ExecutorToolkit] classifyTextSemantics, text={}", safeText.substring(0, Math.min(50, safeText.length())));
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("text", safeText);
            if (labelsJson != null && !labelsJson.isBlank()) {
                JsonNode labels = objectMapper.readTree(labelsJson);
                if (labels.isArray()) {
                    params.set("labels", labels);
                }
            }
            if (topK != null) {
                params.put("topK", topK);
            }
            if (threshold != null) {
                params.put("threshold", threshold);
            }
            return zhengyanTextClassificationTool.execute(objectMapper.writeValueAsString(params)).toJsonString();
        } catch (Exception e) {
            log.warn("[ExecutorToolkit] classifyTextSemantics 参数处理失败: {}", e.getMessage());
            return ToolResponse.toJson(objectMapper,
                    ToolResponse.failure("参数格式错误，labelsJson应为JSON数组字符串"));
        }
    }

    /**
     * 正言图片识别转文本
     */
    @Tool(description = "调用正言 img2text 接口。输入 text(提问)、imageBase64(图片base64)、userInfoJson(包含user_id/user_name/user_dept_name/user_company)。")
    public String img2Text(String text, String imageBase64, String userInfoJson) {
        String safeText = text == null ? "" : text;
        log.info("[ExecutorToolkit] img2Text, text={}", safeText.substring(0, Math.min(50, safeText.length())));
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("text", safeText);
            params.put("image", imageBase64 == null ? "" : imageBase64);
            JsonNode userInfo = objectMapper.readTree(userInfoJson == null ? "{}" : userInfoJson);
            params.set("user_info", userInfo);
            return zhengyanPlatformTool.img2Text(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            log.warn("[ExecutorToolkit] img2Text 参数处理失败: {}", e.getMessage());
            return ToolResponse.toJson(objectMapper,
                    ToolResponse.failure("参数格式错误，userInfoJson需为JSON对象"));
        }
    }

    /**
     * 大智部通用OCR识别（generalRecognition）
     */
    @Tool(description = "调用大智部通用OCR识别接口，一般用于对话中读取图片/证件内容。" +
            "requestJson为原始请求JSON字符串（与大智部接口约定一致），工具会自动补充businessNo和appCode。")
    public String dazhiOcrGeneral(String requestJson) {
        log.info("[ExecutorToolkit] dazhiOcrGeneral 调用");
        return dazhiOcrTool.execute(requestJson == null ? "{}" : requestJson).toJsonString();
    }

    /**
     * 检测数据异常（超出均值3倍标准差等情况）
     */
    @Tool(description = "检测JSON格式数据中的异常值（超出均值3倍或存在极端偏差），" +
            "返回JSON：{\"hasAnomaly\":true/false,\"anomalies\":[{\"field\":\"字段名\",\"value\":数值,\"threshold\":阈值,\"description\":\"说明\"}]}")
    public String checkDataAnomaly(String dataJson) {
        log.info("[ExecutorToolkit] checkDataAnomaly");
        try {
            JsonNode root = objectMapper.readTree(dataJson);
            List<String> anomalies = new ArrayList<>();

            // 遍历数字字段，收集数值
            List<Double> values = new ArrayList<>();
            root.properties().forEach(entry -> {
                if (entry.getValue().isNumber()) {
                    values.add(entry.getValue().asDouble());
                }
            });

            if (values.size() >= 2) {
                double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double variance = values.stream()
                        .mapToDouble(v -> Math.pow(v - mean, 2))
                        .average().orElse(0);
                double stdDev = Math.sqrt(variance);
                double threshold = mean + 3 * stdDev;

                int[] idx = {0};
                root.properties().forEach(entry -> {
                    if (entry.getValue().isNumber()) {
                        double val = entry.getValue().asDouble();
                        if (val > threshold && threshold > 0) {
                            anomalies.add(String.format(
                                    "{\"field\":\"%s\",\"value\":%s,\"threshold\":%.2f,\"description\":\"超出均值3倍标准差\"}",
                                    entry.getKey(), val, threshold));
                        }
                        idx[0]++;
                    }
                });
            }

            boolean hasAnomaly = !anomalies.isEmpty();
            return String.format("{\"hasAnomaly\":%b,\"anomalies\":[%s]}",
                    hasAnomaly, String.join(",", anomalies));

        } catch (Exception e) {
            log.warn("[ExecutorToolkit] checkDataAnomaly 解析失败: {}", e.getMessage());
            return "{\"hasAnomaly\":false,\"anomalies\":[],\"error\":\"数据解析失败\"}";
        }
    }
}
