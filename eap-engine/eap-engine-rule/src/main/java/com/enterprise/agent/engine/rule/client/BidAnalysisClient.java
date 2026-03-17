package com.enterprise.agent.engine.rule.client;

import com.enterprise.agent.engine.rule.client.dto.BidAnalysisCompareBase64Request;
import com.enterprise.agent.engine.rule.client.dto.BidAnalysisCompareBase64Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Python bid-analysis-service HTTP Client
 */
@FeignClient(name = "bid-analysis-service", url = "${eap.audit.collusive.python.base-url:http://localhost:8099}")
public interface BidAnalysisClient {

    @PostMapping("/analyze/compare-base64")
    BidAnalysisCompareBase64Response compareBase64(@RequestBody BidAnalysisCompareBase64Request request);
}

