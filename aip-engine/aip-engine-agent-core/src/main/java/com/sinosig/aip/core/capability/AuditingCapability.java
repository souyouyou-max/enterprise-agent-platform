package com.sinosig.aip.core.capability;

/**
 * 机构稽核能力抽象，供上层编排按接口调用，避免业务模块横向直接依赖。
 */
public interface AuditingCapability {

    String discoverClues(String orgCode);

    String analyzeRisk(String orgCode);

    String checkMonitoring(String orgCode);
}
