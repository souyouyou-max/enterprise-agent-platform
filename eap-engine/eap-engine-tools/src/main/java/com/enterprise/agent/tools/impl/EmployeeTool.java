package com.enterprise.agent.tools.impl;

import com.enterprise.agent.common.core.response.ToolResponse;
import com.enterprise.agent.tools.EnterpriseTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * EmployeeTool - 员工信息查询工具（Mock 实现）
 */
@Slf4j
@Component
public class EmployeeTool implements EnterpriseTool {

    private final ObjectMapper objectMapper;

    // Mock 员工数据库
    private static final Map<String, Object[]> EMPLOYEE_DB = Map.of(
            "E001", new Object[]{"张明", "华南区", "销售总监", "zhang.ming@enterprise.com", 15, "高级"},
            "E002", new Object[]{"李华", "华北区", "销售经理", "li.hua@enterprise.com", 8, "中级"},
            "E003", new Object[]{"王芳", "华东区", "区域总监", "wang.fang@enterprise.com", 12, "高级"},
            "E004", new Object[]{"陈强", "西部区", "销售顾问", "chen.qiang@enterprise.com", 5, "初级"},
            "E005", new Object[]{"刘洋", "总部", "CTO", "liu.yang@enterprise.com", 20, "专家"}
    );

    public EmployeeTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getToolName() {
        return "getEmployeeInfo";
    }

    @Override
    public String getDescription() {
        return "查询员工基本信息（姓名、部门、职位、邮箱、工龄、级别）";
    }

    @Override
    public ToolResponse execute(String params) {
        log.info("[EmployeeTool] 查询员工, params={}", params);
        try {
            String employeeId = "E001"; // 默认

            if (params != null && !params.isBlank()) {
                JsonNode node = objectMapper.readTree(params);
                if (node.has("id")) employeeId = node.get("id").asText();
                else if (node.has("employeeId")) employeeId = node.get("employeeId").asText();
            }

            return ToolResponse.fromRawJson(buildEmployeeResponse(employeeId));
        } catch (Exception e) {
            log.error("[EmployeeTool] 执行失败: {}", e.getMessage());
            return ToolResponse.failure("员工查询失败");
        }
    }

    private String buildEmployeeResponse(String employeeId) throws Exception {
        ObjectNode result = objectMapper.createObjectNode();

        Object[] data = EMPLOYEE_DB.get(employeeId);
        if (data == null) {
            result.put("found", false);
            result.put("employeeId", employeeId);
            result.put("message", "员工不存在");
        } else {
            result.put("found", true);
            result.put("employeeId", employeeId);
            result.put("name", (String) data[0]);
            result.put("department", (String) data[1]);
            result.put("position", (String) data[2]);
            result.put("email", (String) data[3]);
            result.put("yearsOfService", (Integer) data[4]);
            result.put("level", (String) data[5]);
            result.put("status", "在职");
        }

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }
}
