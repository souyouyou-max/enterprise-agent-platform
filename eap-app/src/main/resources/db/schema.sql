-- ============================================================
-- Enterprise Agent Platform - 数据库初始化脚本
-- 数据库：PostgreSQL 14+
-- ============================================================

-- 创建数据库（如果不存在，需要手动执行）
-- CREATE DATABASE eap_db OWNER eap_user;

-- ============================================================
-- 任务主表
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_task (
    id            BIGSERIAL PRIMARY KEY,
    task_name     VARCHAR(200) NOT NULL,
    goal          TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    planner_result   TEXT,
    executor_result  TEXT,
    reviewer_score   INTEGER CHECK (reviewer_score >= 0 AND reviewer_score <= 100),
    final_report     TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE agent_task IS 'Agent 任务主表';
COMMENT ON COLUMN agent_task.status IS '状态: PENDING/PLANNING/EXECUTING/REVIEWING/COMMUNICATING/COMPLETED/FAILED';
COMMENT ON COLUMN agent_task.reviewer_score IS 'Reviewer 质量评分 0-100';

-- ============================================================
-- 子任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_sub_task (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT REFERENCES agent_task(id) ON DELETE CASCADE,
    sequence    INTEGER NOT NULL,
    description TEXT    NOT NULL,
    tool_name   VARCHAR(100),
    tool_params TEXT,
    result      TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE agent_sub_task IS 'Agent 子任务表';
COMMENT ON COLUMN agent_sub_task.sequence IS '子任务执行顺序（从1开始）';
COMMENT ON COLUMN agent_sub_task.tool_name IS '调用的企业工具名称';
COMMENT ON COLUMN agent_sub_task.status IS '状态: PENDING/EXECUTING/COMPLETED/FAILED';

-- ============================================================
-- 索引
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_agent_task_status ON agent_task(status);
CREATE INDEX IF NOT EXISTS idx_agent_task_created_at ON agent_task(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_sub_task_task_id ON agent_sub_task(task_id);
CREATE INDEX IF NOT EXISTS idx_agent_sub_task_sequence ON agent_sub_task(task_id, sequence);

-- ============================================================
-- 自动更新 updated_at 触发器
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_agent_task_updated_at ON agent_task;
CREATE TRIGGER update_agent_task_updated_at
    BEFORE UPDATE ON agent_task
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 知识文档表（eap-knowledge RAG 模块）
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_document (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    content     TEXT         NOT NULL,
    category    VARCHAR(100),
    embedding   TEXT,                        -- JSON 格式存储 float 向量
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE knowledge_document IS '企业知识文档表（向量化存储）';
COMMENT ON COLUMN knowledge_document.embedding IS 'float 数组 JSON，格式：[0.12, -0.34, ...]';
COMMENT ON COLUMN knowledge_document.category IS '文档分类，如：HR / 财务 / 法务 / 产品';

CREATE INDEX IF NOT EXISTS idx_knowledge_doc_category ON knowledge_document(category);
CREATE INDEX IF NOT EXISTS idx_knowledge_doc_created_at ON knowledge_document(created_at DESC);

-- ============================================================
-- 测试数据（可选）
-- ============================================================
-- INSERT INTO agent_task (task_name, goal, status) VALUES
--     ('华南区Q4销售分析', '分析2024年第四季度华南区销售情况，找出增长点和风险', 'PENDING'),
--     ('员工绩效分析', '综合评估华东区Q4销售团队绩效，生成改进建议', 'PENDING');
