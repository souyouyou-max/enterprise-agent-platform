package com.sinosig.aip.common.core.enums;

public enum ReportStyle {
    EMAIL("邮件风格"),
    SUMMARY("摘要风格"),
    DETAILED("详细分析");

    private final String description;

    ReportStyle(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
