package com.enterprise.agent.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * SQL 查询执行服务
 * - 安全校验：仅允许 SELECT，拒绝 DDL / DML
 * - 使用 JdbcTemplate 执行查询，返回 List<Map> 结构
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQueryService {

    /** 每次查询最多返回行数，防止大数据集压垮内存 */
    private static final int MAX_ROWS = 500;

    private static final java.util.regex.Pattern DANGEROUS_PATTERN =
            java.util.regex.Pattern.compile(
                    "(?i)\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE|EXEC|EXECUTE)\\b");

    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行 SELECT 查询并返回结果集
     *
     * @param sql 待执行的 SQL（必须为 SELECT）
     * @return 查询结果行列表，每行为 Map（列名 → 值）
     * @throws IllegalArgumentException 若 SQL 含有危险操作
     */
    public List<Map<String, Object>> executeQuery(String sql) {
        validateSql(sql);

        // 追加 LIMIT 防止全表扫描返回过多行
        String safeSql = appendLimit(sql, MAX_ROWS);
        log.info("执行查询: {}", safeSql);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(safeSql);
        log.info("查询完成，返回 {} 行", result.size());
        return result;
    }

    /**
     * 安全校验：禁止非 SELECT 语句
     */
    private void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String trimmed = sql.trim();
        if (!trimmed.toUpperCase().startsWith("SELECT")) {
            throw new IllegalArgumentException("仅允许 SELECT 查询，拒绝执行: " + trimmed.substring(0, Math.min(50, trimmed.length())));
        }
        if (DANGEROUS_PATTERN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("SQL 中包含危险关键字，拒绝执行");
        }
    }

    /**
     * 若 SQL 未包含 LIMIT 则自动追加，防止返回过多数据
     */
    private String appendLimit(String sql, int maxRows) {
        String upper = sql.toUpperCase();
        if (upper.contains("LIMIT")) {
            return sql;
        }
        // 去掉末尾分号再追加 LIMIT
        return sql.replaceAll(";\\s*$", "") + " LIMIT " + maxRows;
    }
}
