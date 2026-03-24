package com.sinosig.aip.scheduler.client.dto;

import lombok.Data;

/**
 * 审计结果摘要 DTO（对应 aip-app AuditEngineController 响应体）
 */
@Data
public class AuditSummaryDTO {
    private String orgCode;
    private int totalClues;
    private long highRisk;
    private long mediumRisk;
    private long lowRisk;
}
