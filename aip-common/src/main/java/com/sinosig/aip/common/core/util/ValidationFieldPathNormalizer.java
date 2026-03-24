package com.sinosig.aip.common.core.util;

/**
 * 参数校验字段路径规范化工具。
 */
public final class ValidationFieldPathNormalizer {

    private ValidationFieldPathNormalizer() {
    }

    public static String normalize(String rawFieldPath) {
        if (rawFieldPath == null || rawFieldPath.isBlank()) {
            return rawFieldPath;
        }
        String normalized = rawFieldPath.trim();
        int methodDot = normalized.indexOf('.');
        if (methodDot >= 0) {
            normalized = normalized.substring(methodDot + 1);
        }
        if (normalized.startsWith("request.")) {
            normalized = normalized.substring("request.".length());
        }
        return normalized;
    }
}

