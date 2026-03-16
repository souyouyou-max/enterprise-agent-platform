package com.enterprise.agent.engine.rule.impl;

import com.enterprise.agent.data.entity.ClueResult;
import com.enterprise.agent.data.entity.ProcurementBid;
import com.enterprise.agent.data.mapper.BidDocumentMapper;
import com.enterprise.agent.data.mapper.ClueQueryMapper;
import com.enterprise.agent.engine.rule.AuditRule;
import com.enterprise.agent.engine.rule.analyzer.BidPriceAnalyzer;
import com.enterprise.agent.engine.rule.analyzer.BidSimilarityAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 围标串标规则（CollusiveBidRule）
 *
 * <p><b>维度1（COLLUSIVE_LEGAL_PERSON）：</b>
 * 同一项目多家供应商共享同一法定代表人，判定为围标。
 *
 * <p><b>维度2（COLLUSIVE_DOCUMENT_SIMILARITY）：</b>
 * 同一项目投标文件高度相似（TF-IDF余弦相似度 > 0.6 且 LLM判定 HIGH/MEDIUM），判定为围标。
 *
 * <p><b>维度3（COLLUSIVE_PRICE_PATTERN）：</b>
 * 同一项目报价存在异常模式（完全一致/比例固定/高度集中），判定为串标。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollusiveBidRule implements AuditRule {

    private final ClueQueryMapper clueQueryMapper;
    private final BidDocumentMapper bidDocumentMapper;
    private final BidSimilarityAnalyzer similarityAnalyzer;
    private final BidPriceAnalyzer priceAnalyzer;

    @Override
    public String getRuleName() {
        return "围标串标规则";
    }

    @Override
    public String getClueType() {
        return "COLLUSIVE_BID";
    }

    @Override
    public String getRiskLevel() {
        return "HIGH";
    }

    @Override
    public List<ClueResult> execute(String orgCode) {
        log.info("[规则引擎] 执行规则：{}，机构：{}", getRuleName(), orgCode);

        List<ClueResult> results = new ArrayList<>();

        // ---- 维度1：共同法定代表人 ----
        results.addAll(detectByLegalPerson(orgCode));

        // ---- 维度2：投标文件相似度 ----
        results.addAll(detectByDocumentSimilarity(orgCode));

        // ---- 维度3：报价异常模式 ----
        results.addAll(detectByPricePattern(orgCode));

        log.info("[规则引擎] 规则{}执行完成，命中{}条疑点", getRuleName(), results.size());
        return results;
    }

    // ---- 维度1：共同法定代表人 ----

    private List<ClueResult> detectByLegalPerson(String orgCode) {
        List<Map<String, Object>> hits = clueQueryMapper.findCollusiveBids(orgCode);
        List<ClueResult> results = new ArrayList<>();

        for (Map<String, Object> row : hits) {
            String projectId = str(row, "bid_project_id");
            String projectName = str(row, "project_name");
            String winnerSupplier = str(row, "winner_supplier");
            String colluderSupplier = str(row, "colluder_supplier");
            String sharedLegalPerson = str(row, "shared_legal_person");
            BigDecimal winningAmount = toBigDecimal(row, "winning_amount");

            ClueResult clue = new ClueResult();
            clue.setOrgCode(orgCode);
            clue.setClueType("COLLUSIVE_LEGAL_PERSON");
            clue.setRiskLevel("HIGH");
            clue.setClueTitle(String.format("疑似围标串标（共同法人）：%s（项目：%s）", winnerSupplier, projectName));
            clue.setClueDetail(String.format(
                    "在项目【%s】（项目编号：%s）的招标过程中，" +
                    "中标供应商【%s】与投标供应商【%s】的法定代表人均为【%s】，" +
                    "两家公司由同一实际控制人控制，存在安排围标、串通投标的重大嫌疑。" +
                    "中标金额：%.2f 元。",
                    projectName, projectId,
                    winnerSupplier, colluderSupplier, sharedLegalPerson,
                    winningAmount));
            clue.setRelatedAmount(winningAmount);
            clue.setRelatedSupplier(winnerSupplier + "，" + colluderSupplier);
            clue.setRuleName(getRuleName());
            clue.setStatus("PENDING");
            clue.setCreatedAt(LocalDateTime.now());

            results.add(clue);
            log.warn("[{}][维度1] 命中：项目={}，中标方={}，陪标方={}，共同法人={}",
                    getRuleName(), projectName, winnerSupplier, colluderSupplier, sharedLegalPerson);
        }

        return results;
    }

    // ---- 维度2：投标文件相似度 ----

    private List<ClueResult> detectByDocumentSimilarity(String orgCode) {
        List<ClueResult> results = new ArrayList<>();
        try {
            // 查找该机构下有文档文本（直接文本或MinIO路径）的项目
            List<String> projectIds = bidDocumentMapper.findProjectsWithDocumentText(orgCode);
            for (String projectId : projectIds) {
                List<ProcurementBid> bids = bidDocumentMapper.findBidsWithTextByProject(projectId);
                if (bids.size() < 2) continue;

                List<BidSimilarityAnalyzer.SimilarBidPair> pairs = similarityAnalyzer.analyze(bids);
                for (BidSimilarityAnalyzer.SimilarBidPair pair : pairs) {
                    BidSimilarityAnalyzer.LlmSimilarityResult llm = pair.getLlmResult();
                    if (llm == null) continue;
                    String level = llm.getSuspiciousLevel();
                    if (!"HIGH".equals(level) && !"MEDIUM".equals(level)) continue;

                    ProcurementBid a = pair.getBidA();
                    ProcurementBid b = pair.getBidB();
                    String evidenceStr = llm.getEvidences() != null
                            ? String.join("；", llm.getEvidences()) : "";

                    ClueResult clue = new ClueResult();
                    clue.setOrgCode(orgCode);
                    clue.setClueType("COLLUSIVE_DOCUMENT_SIMILARITY");
                    clue.setRiskLevel("HIGH".equals(level) ? "HIGH" : "MEDIUM");
                    clue.setClueTitle(String.format("疑似围标串标（文件相似）：%s vs %s（项目：%s）",
                            a.getSupplierName(), b.getSupplierName(), a.getProjectName()));
                    clue.setClueDetail(String.format(
                            "在项目【%s】（项目编号：%s）中，" +
                            "【%s】与【%s】的投标文件余弦相似度为%.2f，LLM相似度评分%d分，疑似程度：%s。" +
                            "主要证据：%s。结论：%s",
                            a.getProjectName(), projectId,
                            a.getSupplierName(), b.getSupplierName(),
                            pair.getCosineSimilarity(), llm.getSimilarityScore(), level,
                            evidenceStr, llm.getConclusion()));
                    clue.setRelatedAmount(a.getBidAmount() != null ? a.getBidAmount() : BigDecimal.ZERO);
                    clue.setRelatedSupplier(a.getSupplierName() + "，" + b.getSupplierName());
                    clue.setRuleName(getRuleName());
                    clue.setStatus("PENDING");
                    clue.setCreatedAt(LocalDateTime.now());

                    results.add(clue);
                    log.warn("[{}][维度2] 命中：项目={}，{}vs{}，cosine={}，LLM评分={}，level={}",
                            getRuleName(), projectId, a.getSupplierId(), b.getSupplierId(),
                            pair.getCosineSimilarity(), llm.getSimilarityScore(), level);
                }
            }
        } catch (Exception e) {
            log.warn("[{}][维度2] 文件相似度检测异常，跳过：{}", getRuleName(), e.getMessage());
        }
        return results;
    }

    // ---- 维度3：报价异常模式 ----

    private List<ClueResult> detectByPricePattern(String orgCode) {
        List<ClueResult> results = new ArrayList<>();
        try {
            // 查询该机构下所有项目的投标记录，按项目分组
            List<String> allProjectIds = clueQueryMapper.findProjectIdsByOrgCode(orgCode);
            for (String projectId : allProjectIds) {
                List<ProcurementBid> bids = bidDocumentMapper.findAllBidsByProject(projectId);
                if (bids.size() < 2) continue;

                BidPriceAnalyzer.PricePatternResult pattern = priceAnalyzer.analyze(bids);
                if (!pattern.isHasSuspiciousPattern()) continue;

                String projectName = bids.get(0).getProjectName();
                String suppliers = bids.stream()
                        .map(ProcurementBid::getSupplierName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("，"));
                BigDecimal totalAmount = bids.stream()
                        .map(ProcurementBid::getBidAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                ClueResult clue = new ClueResult();
                clue.setOrgCode(orgCode);
                clue.setClueType("COLLUSIVE_PRICE_PATTERN");
                clue.setRiskLevel("HIGH");
                clue.setClueTitle(String.format("疑似串通投标（报价异常）：%s（项目：%s）",
                        pattern.getPatternType(), projectName));
                clue.setClueDetail(String.format(
                        "在项目【%s】（项目编号：%s）中，%d家供应商报价存在异常模式【%s】。%s涉及供应商：%s。",
                        projectName, projectId, bids.size(), pattern.getPatternType(),
                        pattern.getDescription(), suppliers));
                clue.setRelatedAmount(totalAmount);
                clue.setRelatedSupplier(suppliers);
                clue.setRuleName(getRuleName());
                clue.setStatus("PENDING");
                clue.setCreatedAt(LocalDateTime.now());

                results.add(clue);
                log.warn("[{}][维度3] 命中：项目={}，模式={}，描述={}",
                        getRuleName(), projectId, pattern.getPatternType(), pattern.getDescription());
            }
        } catch (Exception e) {
            log.warn("[{}][维度3] 报价模式检测异常，跳过：{}", getRuleName(), e.getMessage());
        }
        return results;
    }

    // ---- 工具方法 ----

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
    }

    private BigDecimal toBigDecimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }
}
