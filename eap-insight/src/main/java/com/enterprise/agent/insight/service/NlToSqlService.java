package com.enterprise.agent.insight.service;

import com.enterprise.agent.common.ai.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言转 SQL 服务（NL2SQL）
 * 将用户问题结合内置 Schema 上下文，调用 LLM 生成可执行的 PostgreSQL 查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NlToSqlService {

    private static final String SYSTEM_PROMPT =
            "你是 SQL 专家，根据以下 PostgreSQL 表结构将用户问题转换为 SQL 查询语句。\n" +
            "要求：\n" +
            "1. 只返回 SQL 语句，不要任何解释、注释或 Markdown 代码块\n" +
            "2. 只生成 SELECT 语句，禁止 INSERT/UPDATE/DELETE/DROP 等操作\n" +
            "3. 使用标准 PostgreSQL 语法\n" +
            "4. 字段名使用双引号包裹以避免关键字冲突";

    /**
     * 内置 Mock Schema：三张业务表的建表语句，作为 LLM 上下文
     * 生产环境可从元数据库动态获取
     */
    public static final String MOCK_SCHEMA = """
            -- 销售数据表
            CREATE TABLE sales_data (
                id          BIGSERIAL PRIMARY KEY,
                dept        VARCHAR(100) NOT NULL COMMENT '部门名称',
                quarter     VARCHAR(20)  NOT NULL COMMENT '季度，如 Q1-2024',
                revenue     NUMERIC(15,2)         COMMENT '销售额（元）',
                target      NUMERIC(15,2)         COMMENT '销售目标（元）',
                order_count INTEGER               COMMENT '订单数量',
                created_at  TIMESTAMP DEFAULT NOW()
            );

            -- 员工信息表
            CREATE TABLE employee (
                id           BIGSERIAL PRIMARY KEY,
                emp_id       VARCHAR(50)  UNIQUE NOT NULL COMMENT '员工编号',
                name         VARCHAR(100) NOT NULL COMMENT '姓名',
                dept         VARCHAR(100)         COMMENT '部门',
                position     VARCHAR(100)         COMMENT '职位',
                performance  NUMERIC(5,2)         COMMENT '绩效评分（0-100）',
                salary       NUMERIC(12,2)        COMMENT '薪资（元/月）',
                hire_date    DATE                 COMMENT '入职日期'
            );

            -- CRM 订单表
            CREATE TABLE crm_order (
                id            BIGSERIAL PRIMARY KEY,
                customer_id   VARCHAR(50)   NOT NULL COMMENT '客户编号',
                customer_name VARCHAR(200)           COMMENT '客户名称',
                product       VARCHAR(200)           COMMENT '产品名称',
                amount        NUMERIC(15,2)          COMMENT '订单金额（元）',
                status        VARCHAR(50)            COMMENT '状态: 成交/跟进中/流失',
                sales_rep     VARCHAR(100)           COMMENT '负责销售',
                order_date    DATE                   COMMENT '下单日期'
            );
            """;

    private static final Pattern SQL_PATTERN = Pattern.compile(
            "(?i)(SELECT\\s[\\s\\S]+?)(?:;|$)", Pattern.MULTILINE);

    private final LlmService llmService;

    /**
     * 根据自然语言问题和表 Schema 生成 SQL
     *
     * @param question    用户问题
     * @param tableSchema 表结构（DDL），传 null 则使用内置 Mock Schema
     * @return 生成的 SQL 字符串
     */
    public String generateSql(String question, String tableSchema) {
        String schema = (tableSchema != null && !tableSchema.isBlank()) ? tableSchema : MOCK_SCHEMA;
        String userMessage = String.format("表结构：\n%s\n\n问题：%s", schema, question);

        log.info("NL2SQL 转换中: {}", question);
        String raw = llmService.chatWithSystem(SYSTEM_PROMPT, userMessage);
        String sql = extractSql(raw);
        log.info("生成 SQL: {}", sql);
        return sql;
    }

    /**
     * 从 LLM 输出中提取纯 SQL（去除 Markdown 代码块等噪音）
     */
    private String extractSql(String raw) {
        if (raw == null || raw.isBlank()) return "";

        // 尝试去除 ```sql ... ``` 包裹
        String cleaned = raw.replaceAll("(?i)```sql", "").replaceAll("```", "").trim();

        // 从输出中匹配 SELECT 语句
        Matcher matcher = SQL_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return cleaned;
    }
}
