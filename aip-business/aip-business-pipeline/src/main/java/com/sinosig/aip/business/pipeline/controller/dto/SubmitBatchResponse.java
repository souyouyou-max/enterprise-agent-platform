package com.sinosig.aip.business.pipeline.controller.dto;

import com.sinosig.aip.data.entity.OcrPipelineBatch;
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

    public static SubmitBatchResponse from(OcrPipelineBatch batch) {
        return new SubmitBatchResponse(
                batch.getBatchNo(),
                batch.getId() == null ? null : String.valueOf(batch.getId()),
                batch.getStatus(),
                batch.getTotalFiles());
    }
}
