package com.enterprise.agent.engine.rule.analyzer;

import com.enterprise.agent.data.entity.ProcurementBid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 投标报价异常分析器（围标维度3）
 * 检测报价完全一致、比例固定、差距过小等可疑模式
 */
@Slf4j
@Component
public class BidPriceAnalyzer {

    private static final double FIXED_RATIO_DEVIATION = 0.02; // 2% 偏差内视为固定比例
    private static final double CONCENTRATED_THRESHOLD = 0.05; // 最高最低差距 < 5%

    /**
     * 分析一批投标的报价，检测可疑模式
     */
    public PricePatternResult analyze(List<ProcurementBid> bids) {
        List<BigDecimal> prices = bids.stream()
                .map(ProcurementBid::getBidAmount)
                .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        if (prices.size() < 2) {
            return notSuspicious(prices);
        }

        // 检测1：所有报价完全一致
        boolean allSame = prices.stream().distinct().count() == 1;
        if (allSame) {
            PricePatternResult r = new PricePatternResult();
            r.setHasSuspiciousPattern(true);
            r.setPatternType("IDENTICAL_PRICES");
            r.setDescription(String.format("同一项目所有投标报价完全相同（%s元），高度疑似串通投标。",
                    prices.get(0).toPlainString()));
            r.setPrices(prices);
            r.setRatio(null);
            log.warn("[BidPriceAnalyzer] 检测到报价完全一致：{}", prices);
            return r;
        }

        BigDecimal min = prices.stream().min(BigDecimal::compareTo).get();
        BigDecimal max = prices.stream().max(BigDecimal::compareTo).get();

        // 检测3：最低与最高差距 < 5%（优先于检测2检查）
        if (min.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = max.subtract(min);
            BigDecimal diffRatio = diff.divide(min, 6, RoundingMode.HALF_UP);
            if (diffRatio.doubleValue() < CONCENTRATED_THRESHOLD) {
                PricePatternResult r = new PricePatternResult();
                r.setHasSuspiciousPattern(true);
                r.setPatternType("CONCENTRATED_PRICES");
                r.setDescription(String.format(
                        "所有投标报价高度集中，最低价%s元，最高价%s元，差距仅%.2f%%，疑似事先协商报价区间。",
                        min.toPlainString(), max.toPlainString(), diffRatio.doubleValue() * 100));
                r.setPrices(prices);
                r.setRatio(diffRatio.doubleValue());
                log.warn("[BidPriceAnalyzer] 检测到报价异常集中：min={}，max={}，差距={}", min, max, diffRatio);
                return r;
            }
        }

        // 检测2：两两报价比例固定（偏差 < 2%）
        if (prices.size() >= 2) {
            BigDecimal baseRatio = prices.get(0).divide(prices.get(1), 6, RoundingMode.HALF_UP);
            boolean fixedRatio = true;
            for (int i = 0; i < prices.size() - 1; i++) {
                for (int j = i + 1; j < prices.size(); j++) {
                    BigDecimal ratio = prices.get(i).divide(prices.get(j), 6, RoundingMode.HALF_UP);
                    double deviation = Math.abs(ratio.doubleValue() - baseRatio.doubleValue()) / baseRatio.doubleValue();
                    if (deviation >= FIXED_RATIO_DEVIATION) {
                        fixedRatio = false;
                        break;
                    }
                }
                if (!fixedRatio) break;
            }
            if (fixedRatio && prices.size() > 2) {
                PricePatternResult r = new PricePatternResult();
                r.setHasSuspiciousPattern(true);
                r.setPatternType("FIXED_RATIO_PRICES");
                r.setDescription(String.format(
                        "各投标报价比例高度固定（基准比例约%.4f，偏差<2%%），疑似按预设比例分配报价。",
                        baseRatio.doubleValue()));
                r.setPrices(prices);
                r.setRatio(baseRatio.doubleValue());
                log.warn("[BidPriceAnalyzer] 检测到固定比例报价：ratio={}", baseRatio);
                return r;
            }
        }

        return notSuspicious(prices);
    }

    private PricePatternResult notSuspicious(List<BigDecimal> prices) {
        PricePatternResult r = new PricePatternResult();
        r.setHasSuspiciousPattern(false);
        r.setPatternType("NORMAL");
        r.setDescription("报价分布正常，未发现可疑模式。");
        r.setPrices(prices);
        r.setRatio(null);
        return r;
    }

    @Data
    public static class PricePatternResult {
        private boolean hasSuspiciousPattern;
        private String patternType;
        private String description;
        private List<BigDecimal> prices;
        private Double ratio;
    }
}
