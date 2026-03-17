package com.enterprise.agent.engine.rule.impl;

import com.enterprise.agent.data.entity.ClueResult;
import com.enterprise.agent.engine.rule.AuditRule;
import com.enterprise.agent.engine.rule.client.BidAnalysisClient;
import com.enterprise.agent.engine.rule.client.dto.BidAnalysisBase64File;
import com.enterprise.agent.engine.rule.client.dto.BidAnalysisCompareBase64Request;
import com.enterprise.agent.engine.rule.client.dto.BidAnalysisCompareBase64Response;
import com.enterprise.agent.engine.rule.util.MinioDocumentTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 围标串标：调用 Python bid-analysis-service 做文本/相似度/证据检测
 *
 * 只依赖 MinIO 文件列表（objects），不依赖 procurement_* 等表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
//@ConditionalOnProperty(prefix = "eap.audit.collusive.python", name = "enabled", havingValue = "true")
public class CollusiveBidPythonRule implements AuditRule {

    private static final String RULE_NAME = "围标串标规则（Python对比服务）";
    private static final String CLUE_TYPE = "COLLUSIVE_DOCUMENT_SIMILARITY";
    private static final String DEFAULT_RISK = "HIGH";

    @Value("${eap.audit.collusive.minio.bucket:bid-documents}")
    private String bucket;

    @Value("${eap.audit.collusive.minio.objects:}")
    private String objectsCsv;

    @Value("${eap.audit.collusive.python.min-common-chars:500}")
    private int minCommonChars;

    @Value("${eap.audit.collusive.python.min-same-places:10}")
    private int minSamePlaces;

    private final BidAnalysisClient bidAnalysisClient;
    private final MinioDocumentTextExtractor minio;

    @Override
    public String getRuleName() {
        return RULE_NAME;
    }

    @Override
    public String getClueType() {
        return CLUE_TYPE;
    }

    @Override
    public String getRiskLevel() {
        return DEFAULT_RISK;
    }

    @Override
    public List<ClueResult> execute(String applyCode) {
        List<String> objects = parseObjects(objectsCsv);
        if (objects.size() < 2) return List.of();

        List<BidAnalysisBase64File> files = new ArrayList<>();
        for (String obj : objects) {
            byte[] bytes = minio.fetchObjectBytes(bucket, obj);
            if (bytes == null || bytes.length == 0) continue;
            BidAnalysisBase64File f = new BidAnalysisBase64File();
            f.setFilename(obj);
            f.setContent_b64(Base64.getEncoder().encodeToString(bytes));
            files.add(f);
        }
        if (files.size() < 2) return List.of();

        BidAnalysisCompareBase64Request req = new BidAnalysisCompareBase64Request();
        req.setFiles(files);

        BidAnalysisCompareBase64Response resp = bidAnalysisClient.compareBase64(req);
        log.info("[CollusiveBidPythonRule] Python对比服务响应：{}", resp);
        if (resp == null || resp.getComparisons() == null) return List.of();

        List<ClueResult> results = new ArrayList<>();
        for (BidAnalysisCompareBase64Response.FileComparison c : resp.getComparisons()) {
            Map<String, Object> r = c.getResult();
            if (r == null) continue;
            if (!Boolean.TRUE.equals(r.get("ok"))) continue;

            int longest = asInt(r.get("longestCommonRunChars"));
            int segs50 = asInt(r.get("matchingSegments50+"));
            int blocks500 = asInt(r.get("commonBlocksCount500+"));

            boolean abnormal = longest >= minCommonChars || segs50 >= minSamePlaces || blocks500 > 0;
            if (!abnormal) continue;

            ClueResult clue = new ClueResult();
            clue.setApplyCode(applyCode);
            clue.setClueType(getClueType());
            clue.setRiskLevel(getRiskLevel());
            clue.setClueTitle("疑似围标串标（Python对比）：投标文件相同程度异常");
            clue.setClueDetail(String.format(
                    "申请编码【%s】下，Python对比服务命中：\n- A: %s\n- B: %s\n" +
                            "longestCommonRunChars=%d（阈值=%d）\n" +
                            "matchingSegments50+=%d（阈值=%d）\n" +
                            "commonBlocksCount500+=%d\n" +
                            "tfidfCosine=%s, difflibRatio=%s\n",
                    applyCode, c.getA(), c.getB(),
                    longest, minCommonChars,
                    segs50, minSamePlaces,
                    blocks500,
                    String.valueOf(r.get("tfidfCosine")),
                    String.valueOf(r.get("difflibRatio"))
            ));
            clue.setRelatedAmount(BigDecimal.ZERO);
            clue.setRelatedSupplier("未知（MinIO文件对比）");
            clue.setRuleName(getRuleName());
            clue.setStatus("PENDING");
            clue.setCreatedAt(LocalDateTime.now());
            results.add(clue);
        }
        return results;
    }

    private int asInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private List<String> parseObjects(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        String[] parts = csv.split("[,\\n]");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p == null) continue;
            String s = p.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}

