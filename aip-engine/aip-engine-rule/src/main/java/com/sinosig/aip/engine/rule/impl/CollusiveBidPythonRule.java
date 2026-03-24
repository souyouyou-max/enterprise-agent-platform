package com.sinosig.aip.engine.rule.impl;

import com.sinosig.aip.data.entity.ClueResult;
import com.sinosig.aip.engine.rule.AuditRule;
import com.sinosig.aip.engine.rule.client.BidAnalysisClient;
import com.sinosig.aip.engine.rule.client.dto.BidAnalysisBase64File;
import com.sinosig.aip.engine.rule.client.dto.BidAnalysisCompareBase64Request;
import com.sinosig.aip.engine.rule.client.dto.BidAnalysisCompareBase64Response;
import com.sinosig.aip.engine.rule.util.MinioDocumentTextExtractor;
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
//@ConditionalOnProperty(prefix = "aip.audit.collusive.python", name = "enabled", havingValue = "true")
public class CollusiveBidPythonRule implements AuditRule {

    private static final String RULE_NAME = "围标串标规则（Python对比服务）";
    private static final String CLUE_TYPE = "COLLUSIVE_DOCUMENT_SIMILARITY";
    private static final String DEFAULT_RISK = "HIGH";

    @Value("${aip.audit.collusive.minio.bucket:bid-documents}")
    private String bucket;

    @Value("${aip.audit.collusive.minio.objects:}")
    private String objectsCsv;

    @Value("${aip.audit.collusive.python.min-common-chars:500}")
    private int minCommonChars;

    @Value("${aip.audit.collusive.python.min-same-places:10}")
    private int minSamePlaces;

    /** 余弦相似度触发阈值（当提取文字较短时仍可告警） */
    @Value("${aip.audit.collusive.python.cosine-threshold:0.95}")
    private double cosineThreshold;

    /** 触发余弦判断所需最小文本长度（避免空文档误报） */
    @Value("${aip.audit.collusive.python.min-text-len-for-cosine:30}")
    private int minTextLenForCosine;

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
        if (resp == null || resp.getComparisons() == null) return List.of();
        if (resp.getFileMetas() != null) {
            resp.getFileMetas().forEach(m -> {
                String preview = String.valueOf(m.getOrDefault("textPreview", ""));
                String previewOneline = preview.replaceAll("\\s+", " ").trim();
                log.info("[CollusiveBidPythonRule] fileMeta name={} sizeBytes={} textLen={} sha256={} content={}",
                        m.get("name"), m.get("sizeBytes"), m.get("textLen"), m.get("sha256"), previewOneline);
            });
        }
        resp.getComparisons().forEach(c -> {
            Map<String, Object> r = c.getResult();
            if (r == null) return;
            log.info("[CollusiveBidPythonRule] comparison A={} B={} cosine={} difflib={} longest={} segs50+={} blocks500+={} lenA={} lenB={}",
                    c.getA(), c.getB(),
                    r.get("tfidfCosine"), r.get("difflibRatio"),
                    r.get("longestCommonRunChars"), r.get("matchingSegments50+"), r.get("commonBlocksCount500+"),
                    r.get("lenA"), r.get("lenB"));
        });

        List<ClueResult> results = new ArrayList<>();
        for (BidAnalysisCompareBase64Response.FileComparison c : resp.getComparisons()) {
            Map<String, Object> r = c.getResult();
            if (r == null) continue;
            if (!Boolean.TRUE.equals(r.get("ok"))) continue;

            int longest = asInt(r.get("longestCommonRunChars"));
            int segs50 = asInt(r.get("matchingSegments50+"));
            int blocks500 = asInt(r.get("commonBlocksCount500+"));
            double cosine = asDouble(r.get("tfidfCosine"));
            double difflib = asDouble(r.get("difflibRatio"));
            int lenA = asInt(r.get("lenA"));
            int lenB = asInt(r.get("lenB"));

            // 常规：长公共块 / 多处相同片段 / 500+字符块
            boolean byBlock = longest >= minCommonChars || segs50 >= minSamePlaces || blocks500 > 0;
            // 补充：文字虽少（如扫描件OCR不全），但余弦+difflib双高 → 亦视为异常
            boolean byCosine = cosine >= cosineThreshold
                    && difflib >= cosineThreshold
                    && lenA >= minTextLenForCosine
                    && lenB >= minTextLenForCosine;

            boolean abnormal = byBlock || byCosine;
            if (!abnormal) continue;

            ClueResult clue = new ClueResult();
            clue.setApplyCode(applyCode);
            clue.setClueType(getClueType());
            clue.setRiskLevel(getRiskLevel());
            String triggerReason = byBlock
                    ? String.format("文本块命中（longestCommonRun=%d,segs50+=%d,blocks500+=%d）", longest, segs50, blocks500)
                    : String.format("余弦双高命中（tfidfCosine=%.4f,difflibRatio=%.4f,文本长度A=%d/B=%d）", cosine, difflib, lenA, lenB);

            clue.setClueTitle("疑似围标串标（Python对比）：投标文件相同程度异常");
            clue.setClueDetail(String.format(
                    "申请[%s] A:%s B:%s | 触发:%s | longest=%d(阈值%d) segs50+=%d(阈值%d) blocks500+=%d | cosine=%.4f difflib=%.4f(阈值%.2f) | lenA=%d lenB=%d",
                    applyCode, c.getA(), c.getB(),
                    triggerReason,
                    longest, minCommonChars,
                    segs50, minSamePlaces,
                    blocks500,
                    cosine, difflib, cosineThreshold,
                    lenA, lenB
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

    private double asDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return 0.0;
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

