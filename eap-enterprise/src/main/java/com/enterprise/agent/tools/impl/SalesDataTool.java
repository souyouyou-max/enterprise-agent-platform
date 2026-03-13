package com.enterprise.agent.tools.impl;

import com.enterprise.agent.tools.EnterpriseTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * SalesDataTool - 销售数据查询工具（Mock 实现）
 * 按部门/季度返回销售数据
 */
@Slf4j
@Component
public class SalesDataTool implements EnterpriseTool {

    private final ObjectMapper objectMapper;
    private final Random random = new Random(42); // 固定种子确保可重现

    public SalesDataTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getToolName() {
        return "getSalesData";
    }

    @Override
    public String getDescription() {
        return "按部门和季度查询企业销售数据，返回销售额、订单数、增长率等指标";
    }

    @Override
    public String execute(String params) {
        log.info("[SalesDataTool] 执行查询, params={}", params);
        try {
            String department = "all";
            String quarter = "Q4-2024";

            if (params != null && !params.isBlank() && !params.equals("{}")) {
                JsonNode node = objectMapper.readTree(params);
                if (node.has("department")) department = node.get("department").asText();
                if (node.has("quarter")) quarter = node.get("quarter").asText();
            }

            return generateSalesData(department, quarter);
        } catch (Exception e) {
            log.error("[SalesDataTool] 执行失败: {}", e.getMessage());
            return generateSalesData("all", "Q4-2024");
        }
    }

    private String generateSalesData(String department, String quarter) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("department", department);
            root.put("quarter", quarter);
            root.put("currency", "CNY");

            // 各部门基础数据
            String[] depts = "all".equals(department)
                ? new String[]{"华南区", "华北区", "华东区", "西部区"}
                : new String[]{department};

            ArrayNode deptArray = root.putArray("departments");
            double totalRevenue = 0;
            int totalOrders = 0;

            for (String dept : depts) {
                int base = getDeptBase(dept);
                double revenue = base * (0.9 + Math.abs(random.nextGaussian()) * 0.1) * 1000000;
                int orders = base * 100 + random.nextInt(500);
                double growth = (random.nextDouble() - 0.2) * 0.5; // -20% ~ +30%
                double avgOrderValue = revenue / orders;

                ObjectNode deptNode = deptArray.addObject();
                deptNode.put("name", dept);
                deptNode.put("revenue", Math.round(revenue * 100.0) / 100.0);
                deptNode.put("orders", orders);
                deptNode.put("growthRate", String.format("%.2f%%", growth * 100));
                deptNode.put("avgOrderValue", Math.round(avgOrderValue * 100.0) / 100.0);
                deptNode.put("topProduct", getTopProduct(dept));

                totalRevenue += revenue;
                totalOrders += orders;
            }

            ObjectNode summary = root.putObject("summary");
            summary.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
            summary.put("totalOrders", totalOrders);
            summary.put("avgRevenuePerDept", Math.round(totalRevenue / depts.length * 100.0) / 100.0);
            summary.put("reportGeneratedAt", java.time.LocalDateTime.now().toString());

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\": \"数据生成失败\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    private int getDeptBase(String dept) {
        return switch (dept) {
            case "华南区" -> 45;
            case "华北区" -> 38;
            case "华东区" -> 52;
            case "西部区" -> 25;
            default -> 35;
        };
    }

    private String getTopProduct(String dept) {
        return switch (dept) {
            case "华南区" -> "企业软件套件";
            case "华北区" -> "云服务订阅";
            case "华东区" -> "数据分析平台";
            case "西部区" -> "智能制造解决方案";
            default -> "综合解决方案";
        };
    }
}
