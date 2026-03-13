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
 * CrmTool - 客户关系管理数据查询工具（Mock 实现）
 */
@Slf4j
@Component
public class CrmTool implements EnterpriseTool {

    private final ObjectMapper objectMapper;
    private final Random random = new Random(123);

    public CrmTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getToolName() {
        return "queryCrmData";
    }

    @Override
    public String getDescription() {
        return "查询客户信息和订单历史，包括客户等级、合同金额、续约状态等";
    }

    @Override
    public String execute(String params) {
        log.info("[CrmTool] 查询CRM数据, params={}", params);
        try {
            String customerId = "C001";
            if (params != null && !params.isBlank()) {
                JsonNode node = objectMapper.readTree(params);
                if (node.has("customerId")) customerId = node.get("customerId").asText();
            }
            return buildCrmResponse(customerId);
        } catch (Exception e) {
            log.error("[CrmTool] 执行失败: {}", e.getMessage());
            return "{\"error\": \"CRM查询失败\"}";
        }
    }

    private String buildCrmResponse(String customerId) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("customerId", customerId);
        root.put("companyName", getCompanyName(customerId));
        root.put("industry", getIndustry(customerId));
        root.put("tier", getTier(customerId));
        root.put("contactPerson", "李总监");
        root.put("phone", "138-XXXX-" + customerId.substring(1));
        root.put("totalContractValue", 500000 + random.nextInt(2000000));
        root.put("renewalProbability", String.format("%.0f%%", 60 + random.nextInt(40)));
        root.put("satisfactionScore", 3 + random.nextInt(2) + "." + random.nextInt(10));
        root.put("accountManager", "王芳");

        // 最近订单
        ArrayNode orders = root.putArray("recentOrders");
        for (int i = 1; i <= 3; i++) {
            ObjectNode order = orders.addObject();
            order.put("orderId", "ORD-2024-" + (1000 + i));
            order.put("amount", 100000 + random.nextInt(500000));
            order.put("status", i == 1 ? "进行中" : "已完成");
            order.put("product", i % 2 == 0 ? "企业软件套件" : "云服务订阅");
            order.put("date", "2024-" + (10 + i) + "-01");
        }

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private String getCompanyName(String id) {
        return switch (id) {
            case "C001" -> "深圳科技有限公司";
            case "C002" -> "北京数字集团";
            case "C003" -> "上海创新科技";
            default -> "客户" + id;
        };
    }

    private String getIndustry(String id) {
        return switch (id) {
            case "C001" -> "互联网科技";
            case "C002" -> "金融服务";
            case "C003" -> "制造业";
            default -> "综合";
        };
    }

    private String getTier(String id) {
        return switch (id) {
            case "C001" -> "战略级";
            case "C002" -> "钻石级";
            case "C003" -> "金牌级";
            default -> "银牌级";
        };
    }
}
