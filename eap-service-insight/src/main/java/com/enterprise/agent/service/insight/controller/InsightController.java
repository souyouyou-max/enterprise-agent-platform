package com.enterprise.agent.service.insight.controller;

import com.enterprise.agent.common.core.response.ResponseResult;
import com.enterprise.agent.impl.insight.InsightAgent;
import com.enterprise.agent.insight.model.InsightResult;
import com.enterprise.agent.insight.service.NlToSqlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 智能数据洞察 REST API（NL2BI）
 */
@Tag(name = "数据洞察（NL2BI）", description = "自然语言 → SQL → 执行 → LLM 分析报告")
@RestController
@RequestMapping("/api/v1/insight")
@RequiredArgsConstructor
public class InsightController {

    private final InsightAgent insightAgent;

    @Operation(summary = "自然语言数据分析",
               description = "输入自然语言问题，自动生成 SQL、执行查询、返回 LLM 分析报告")
    @PostMapping("/analyze")
    public ResponseResult<InsightResult> analyze(@Valid @RequestBody AnalyzeRequest request) {
        InsightResult result = insightAgent.investigate(request.getQuestion());
        return ResponseResult.success(result);
    }

    @Operation(summary = "查看可查询的表结构",
               description = "返回内置 Mock Schema（sales_data / employee / crm_order）")
    @GetMapping("/schema")
    public ResponseResult<String> getSchema() {
        return ResponseResult.success(NlToSqlService.MOCK_SCHEMA);
    }

    @Data
    public static class AnalyzeRequest {
        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题不超过500字符")
        private String question;
    }
}
