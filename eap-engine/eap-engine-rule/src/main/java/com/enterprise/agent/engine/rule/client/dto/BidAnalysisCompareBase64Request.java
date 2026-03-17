package com.enterprise.agent.engine.rule.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class BidAnalysisCompareBase64Request {
    private List<BidAnalysisBase64File> files;
}

