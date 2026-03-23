package com.enterprise.agent.business.pipeline.controller.dto;

import lombok.Data;

@Data
public class SubmitBatchResponse {
    private final String batchNo;
    private final String batchId;
    private final String status;
    private final int totalFiles;

    public SubmitBatchResponse(String batchNo, String batchId, String status, int totalFiles) {
        this.batchNo = batchNo;
        this.batchId = batchId;
        this.status = status;
        this.totalFiles = totalFiles;
    }
}
