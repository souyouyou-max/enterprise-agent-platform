package com.enterprise.agent.dataservice.insight.model;

import lombok.Data;

/**
 * 数据洞察请求
 */
@Data
public class InsightRequest {

    /** 自然语言问题，如"上季度哪个部门销售额最高？" */
    private String question;

    /** 数据源标识（可选，扩展用） */
    private String dataSource;

    /** 额外上下文（可选，如时间范围、过滤条件） */
    private String context;
}
