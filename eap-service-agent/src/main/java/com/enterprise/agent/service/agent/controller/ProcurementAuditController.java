package com.enterprise.agent.service.agent.controller;

import com.enterprise.agent.common.core.response.ResponseResult;
import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.data.entity.ProcurementBid;
import com.enterprise.agent.data.entity.ProcurementContract;
import com.enterprise.agent.data.mapper.ProcurementBidMapper;
import com.enterprise.agent.data.mapper.ProcurementContractMapper;
import com.enterprise.agent.impl.procurement.ProcurementAuditAgent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 招采稽核 REST API
 */
@Tag(name = "招采稽核", description = "招采稽核线索发现机器人 - 大额未招标/化整为零/围标串标/利益输送")
@RestController
@RequestMapping("/api/v1/procurement-audit")
@RequiredArgsConstructor
public class ProcurementAuditController {

    private final ProcurementAuditAgent procurementAuditAgent;

    @Operation(summary = "全量稽核", description = "对指定机构执行全部4个场景的招采稽核，返回综合稽核报告")
    @PostMapping("/audit/full")
    public ResponseResult<AgentResult> auditFull(
            @Parameter(description = "机构编码") @RequestParam String orgCode) {
        AgentResult result = procurementAuditAgent.auditAll(orgCode);
        return ResponseResult.success(result);
    }

    @Operation(summary = "单场景稽核",
            description = "执行指定场景的稽核分析。scene 可选值：" +
                    "untendered（大额未招标）/ split（化整为零）/ collusive（围标串标）/ conflict（利益输送）")
    @PostMapping("/audit/scene")
    public ResponseResult<AgentResult> auditScene(
            @Parameter(description = "机构编码") @RequestParam String orgCode,
            @Parameter(description = "场景标识：untendered / split / collusive / conflict")
            @RequestParam String scene) {
        AgentResult result = procurementAuditAgent.auditScene(orgCode, scene);
        return ResponseResult.success(result);
    }

    @Operation(summary = "查询采购合同列表", description = "返回指定机构的所有采购合同（含 Mock 数据）")
    @GetMapping("/contracts")
    public ResponseResult<List<ProcurementContract>> getContracts(
            @Parameter(description = "机构编码") @RequestParam String orgCode) {
        List<ProcurementContract> contracts = ProcurementContractMapper.getMockContracts()
                .stream()
                .filter(c -> orgCode == null || orgCode.equals(c.getOrgCode()))
                .collect(Collectors.toList());
        return ResponseResult.success(contracts);
    }

    @Operation(summary = "查询投标记录", description = "返回指定招标项目的所有投标记录（含 Mock 数据）")
    @GetMapping("/bids/{projectId}")
    public ResponseResult<List<ProcurementBid>> getBids(
            @Parameter(description = "招标项目ID") @PathVariable String projectId) {
        List<ProcurementBid> bids = ProcurementBidMapper.getMockBids()
                .stream()
                .filter(b -> projectId.equals(b.getBidProjectId()))
                .collect(Collectors.toList());
        return ResponseResult.success(bids);
    }
}
