package com.sinosig.aip.common.core.enums;

public enum TaskStatus {
    PENDING("待处理"),
    PLANNING("规划中"),
    EXECUTING("执行中"),
    REVIEWING("审查中"),
    COMMUNICATING("生成报告中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    RETRYING("重试中");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
