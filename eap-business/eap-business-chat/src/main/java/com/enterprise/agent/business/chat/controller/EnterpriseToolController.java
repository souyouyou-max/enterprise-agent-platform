package com.enterprise.agent.business.chat.controller;

import com.enterprise.agent.common.core.response.ResponseResult;
import com.enterprise.agent.tools.ToolRegistry;
import com.enterprise.agent.tools.impl.CrmTool;
import com.enterprise.agent.tools.impl.EmployeeTool;
import com.enterprise.agent.tools.impl.SalesDataTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 企业工具 REST API - 直接调用企业工具（销售数据/员工查询等）
 */
@Tag(name = "企业工具 API", description = "直接查询销售数据、员工信息、CRM 数据等企业工具")
@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseToolController {

    private final SalesDataTool salesDataTool;
    private final EmployeeTool employeeTool;
    private final CrmTool crmTool;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Operation(summary = "查询销售数据", description = "按部门查询季度销售数据（dept 参数可为 华南区/华北区/华东区/西部区/all）")
    @GetMapping("/sales/{dept}")
    public ResponseResult<Object> getSalesData(
            @PathVariable String dept,
            @RequestParam(defaultValue = "Q4-2024") String quarter) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("department", dept);
        params.put("quarter", quarter);
        String result = salesDataTool.execute(objectMapper.writeValueAsString(params));
        return ResponseResult.success(objectMapper.readValue(result, Object.class));
    }

    @Operation(summary = "查询员工信息")
    @GetMapping("/employees/{id}")
    public ResponseResult<Object> getEmployee(@PathVariable String id) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("id", id);
        String result = employeeTool.execute(objectMapper.writeValueAsString(params));
        return ResponseResult.success(objectMapper.readValue(result, Object.class));
    }

    @Operation(summary = "查询 CRM 客户数据")
    @GetMapping("/crm/{customerId}")
    public ResponseResult<Object> getCrmData(@PathVariable String customerId) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("customerId", customerId);
        String result = crmTool.execute(objectMapper.writeValueAsString(params));
        return ResponseResult.success(objectMapper.readValue(result, Object.class));
    }

    @Operation(summary = "查询已注册工具列表")
    @GetMapping("/tools")
    public ResponseResult<Map<String, String>> listTools() {
        return ResponseResult.success(toolRegistry.listTools());
    }
}
