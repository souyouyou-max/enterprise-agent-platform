package com.enterprise.agent.engine.rule.impl;

import com.enterprise.agent.data.entity.ClueResult;
import com.enterprise.agent.engine.rule.AuditRule;
import com.enterprise.agent.engine.rule.util.MinioDocumentTextExtractor;
import com.enterprise.agent.engine.rule.util.TextCosineSimilarity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 围标串标规则（MinIO文件相似度）
 *
 * <p>不依赖 procurement_* / supplier_* / payment_* / internal_employee 等表。
 * 从 MinIO 读取一批投标文件内容，做文本相似度粗筛并产出疑点线索。
 */
@Slf4j
//@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "eap.audit.collusive.minio", name = "enabled", havingValue = "true", matchIfMissing = false)
public class CollusiveBidMinioRule implements AuditRule {

    private static final String RULE_NAME = "围标串标规则（MinIO文件相似度）";
    private static final String CLUE_TYPE = "COLLUSIVE_DOCUMENT_SIMILARITY";
    private static final String DEFAULT_RISK = "HIGH";

    @Value("${eap.audit.collusive.minio.bucket:bid-documents}")
    private String bucket;

    /**
     * MinIO 对象路径列表（逗号分隔）。例如：
     * BID-PROJECT-001/SUP001/a.pdf,BID-PROJECT-001/SUP006/b.pdf
     */
    @Value("${eap.audit.collusive.minio.objects:}")
    private String objectsCsv;

    @Value("${eap.audit.collusive.minio.cosine-threshold:0.60}")
    private double cosineThreshold;

    /**
     * 最大两两对比数量，避免 n^2 爆炸；0 表示不限制
     */
    @Value("${eap.audit.collusive.minio.max-pairs:500}")
    private int maxPairs;

    private final MinioDocumentTextExtractor extractor;

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
        List<ClueResult> results = new ArrayList<>();

        List<String> objects = parseObjects(objectsCsv);
        if (objects.size() < 2) {
            log.info("[{}] 未配置或不足2个MinIO文件，跳过。配置项=eap.audit.collusive.minio.objects", getRuleName());
            return results;
        }

        Map<String, String> textCache = new HashMap<>();
        Map<String, FileMeta> metaCache = new HashMap<>();
        int compared = 0;

        for (int i = 0; i < objects.size(); i++) {
            for (int j = i + 1; j < objects.size(); j++) {
                if (maxPairs > 0 && compared >= maxPairs) {
                    log.warn("[{}] 已达到最大对比数{}，停止继续对比（objects={}）",
                            getRuleName(), maxPairs, objects.size());
                    return results;
                }
                compared++;

                String objA = objects.get(i);
                String objB = objects.get(j);

                String textA = textCache.computeIfAbsent(objA, k -> extractor.extract(bucket, k));
                String textB = textCache.computeIfAbsent(objB, k -> extractor.extract(bucket, k));
                if (textA == null || textA.isBlank() || textB == null || textB.isBlank()) {
                    continue;
                }

                FileMeta metaA = metaCache.computeIfAbsent(objA, k -> buildMeta(bucket, k));
                FileMeta metaB = metaCache.computeIfAbsent(objB, k -> buildMeta(bucket, k));
                boolean sameFile = metaA != null && metaB != null
                        && metaA.sha256 != null && metaA.sha256.equals(metaB.sha256)
                        && metaA.sizeBytes != null && metaA.sizeBytes.equals(metaB.sizeBytes);

                double cosine = TextCosineSimilarity.cosineBigramTf(textA, textB);
                if (!sameFile && cosine < cosineThreshold) {
                    continue;
                }

                ClueResult clue = new ClueResult();
                clue.setApplyCode(applyCode);
                clue.setClueType(getClueType());
                clue.setRiskLevel(getRiskLevel());
                clue.setClueTitle(sameFile
                        ? "重复文件（MinIO）：两份投标文件内容一致"
                        : "疑似围标串标（文件相似）：MinIO投标文件高度相似");
                clue.setClueDetail(String.format(
                        "申请编码【%s】下，MinIO投标文件相似度检测命中：\n" +
                        "- A: %s\n" +
                        "- B: %s\n" +
                        "A(size=%s, sha256=%s)\n" +
                        "B(size=%s, sha256=%s)\n" +
                        "文本余弦相似度=%.4f（阈值=%.2f）。%s",
                        applyCode, objA, objB,
                        metaA != null && metaA.sizeBytes != null ? metaA.sizeBytes : "?",
                        metaA != null ? metaA.sha256 : "?",
                        metaB != null && metaB.sizeBytes != null ? metaB.sizeBytes : "?",
                        metaB != null ? metaB.sha256 : "?",
                        cosine, cosineThreshold,
                        sameFile ? "判定：同一文件/重复上传，建议去重。" : "建议人工复核原始文件与投标单位关系。"));
                clue.setRelatedAmount(BigDecimal.ZERO);
                clue.setRelatedSupplier("未知（仅MinIO文件对比）");
                clue.setRuleName(getRuleName());
                clue.setStatus("PENDING");
                clue.setCreatedAt(LocalDateTime.now());
                results.add(clue);

                log.warn("[{}] 命中：sameFile={} cosine={}，A={}, B={}", getRuleName(), sameFile, cosine, objA, objB);
            }
        }
        return results;
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

    private FileMeta buildMeta(String bucket, String objectPath) {
        try {
            byte[] bytes = extractor.fetchObjectBytes(bucket, objectPath);
            if (bytes == null) return null;
            return new FileMeta(bytes.length, sha256Hex(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class FileMeta {
        private final Integer sizeBytes;
        private final String sha256;

        private FileMeta(Integer sizeBytes, String sha256) {
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }
    }
}

