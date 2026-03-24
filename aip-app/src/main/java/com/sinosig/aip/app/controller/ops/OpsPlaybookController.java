package com.sinosig.aip.app.controller.ops;

import com.sinosig.aip.app.ops.service.PipelineOpsPlaybookService;
import com.sinosig.aip.common.core.response.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一期运维剧本 API：
 * 1) 流水线批次诊断
 * 2) 配置预检（按 extra_info 合并后的有效配置）
 */
@Tag(name = "Ops Playbook", description = "OCR 流水线诊断与配置预检")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops")
public class OpsPlaybookController {

    private final PipelineOpsPlaybookService playbookService;

    @Operation(summary = "诊断 OCR 流水线批次")
    @PostMapping("/pipeline/diagnose")
    public ResponseResult<PipelineOpsPlaybookService.PipelineDiagnosisReport> diagnosePipeline(
            @Valid @RequestBody DiagnoseRequest req) {
        return ResponseResult.success(playbookService.diagnose(req.getBatchNo()));
    }

    @Operation(summary = "预检流水线配置（extra_info 维度）")
    @PostMapping("/config/precheck")
    public ResponseResult<PipelineOpsPlaybookService.ConfigPrecheckReport> precheckConfig(
            @Valid @RequestBody PrecheckRequest req) {
        return ResponseResult.success(playbookService.precheck(req.getExtraInfo()));
    }

    @Operation(summary = "生成配置变更计划（含 Nacos MCP 上下文与白名单校验）")
    @PostMapping("/config/change-plan")
    public ResponseResult<PipelineOpsPlaybookService.ConfigChangePlan> buildChangePlan(
            @Valid @RequestBody ChangePlanRequest req) {
        PipelineOpsPlaybookService.ConfigChangePlanRequest command = new PipelineOpsPlaybookService.ConfigChangePlanRequest(
                req.getNamespace(),
                req.getGroup(),
                req.getDataId(),
                req.getExtraInfo(),
                req.getProposedChanges()
        );
        return ResponseResult.success(playbookService.buildChangePlan(command));
    }

    @Operation(summary = "提交配置变更审批单")
    @PostMapping("/config/submit-approval")
    public ResponseResult<PipelineOpsPlaybookService.ApprovalTicket> submitApproval(
            @Valid @RequestBody SubmitApprovalRequest req) {
        PipelineOpsPlaybookService.ConfigChangePlanRequest command = new PipelineOpsPlaybookService.ConfigChangePlanRequest(
                req.getNamespace(),
                req.getGroup(),
                req.getDataId(),
                req.getExtraInfo(),
                req.getProposedChanges()
        );
        return ResponseResult.success(playbookService.submitForApproval(command, req.getSubmitter(), req.getTicketNo()));
    }

    @Operation(summary = "审批通过后发布配置（调用 Nacos MCP）")
    @PostMapping("/config/approve-publish")
    public ResponseResult<PipelineOpsPlaybookService.PublishResult> approvePublish(
            @Valid @RequestBody ApprovePublishRequest req) {
        return ResponseResult.success(playbookService.approveAndPublish(
                req.getPlanId(),
                req.getApprover(),
                req.getComment()
        ));
    }

    @Data
    public static class DiagnoseRequest {
        @NotBlank(message = "batchNo 不能为空")
        private String batchNo;
    }

    @Data
    public static class PrecheckRequest {
        /**
         * 传 submit-batch 的 extraInfo 字符串（JSON）；空值时仅按全局配置预检。
         */
        private String extraInfo;
    }

    @Data
    public static class ChangePlanRequest {
        /**
         * Nacos namespace，可空（走默认）。
         */
        private String namespace;
        /**
         * Nacos group，可空（默认 DEFAULT_GROUP）。
         */
        private String group;
        /**
         * Nacos dataId，用于读取当前配置与历史。
         */
        @NotBlank(message = "dataId 不能为空")
        private String dataId;
        /**
         * 批次 extraInfo（可空）。
         */
        private String extraInfo;
        /**
         * 拟变更键值对（如 aip.pipeline.analysis.max-images-per-file -> 1）。
         */
        private java.util.Map<String, Object> proposedChanges;
    }

    @Data
    public static class SubmitApprovalRequest {
        private String namespace;
        private String group;
        @NotBlank(message = "dataId 不能为空")
        private String dataId;
        private String extraInfo;
        private java.util.Map<String, Object> proposedChanges;
        @NotBlank(message = "submitter 不能为空")
        private String submitter;
        private String ticketNo;
    }

    @Data
    public static class ApprovePublishRequest {
        @NotBlank(message = "planId 不能为空")
        private String planId;
        @NotBlank(message = "approver 不能为空")
        private String approver;
        private String comment;
    }
}
