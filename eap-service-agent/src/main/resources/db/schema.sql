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
-- 招采稽核模块
-- ============================================================

CREATE TABLE IF NOT EXISTS procurement_contract (
    id               BIGSERIAL PRIMARY KEY,
    org_code         VARCHAR(50),
    project_name     VARCHAR(200),
    supplier_name    VARCHAR(200),
    supplier_id      VARCHAR(100),
    contract_amount  DECIMAL(15,2),
    payment_amount   DECIMAL(15,2),
    contract_date    DATE,
    payment_date     DATE,
    has_zc_process   BOOLEAN DEFAULT FALSE,
    project_category VARCHAR(100),
    deleted          SMALLINT DEFAULT 0,
    created_at       TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE procurement_contract IS '采购合同表（招采稽核-场景1/2）';
COMMENT ON COLUMN procurement_contract.has_zc_process IS '是否有招采流程：true=有，false=无';
COMMENT ON COLUMN procurement_contract.project_category IS '项目类别，如：IT服务/软件开发/办公用品/工程建设';

CREATE TABLE IF NOT EXISTS procurement_bid (
    id             BIGSERIAL PRIMARY KEY,
    bid_project_id VARCHAR(100),
    project_name   VARCHAR(200),
    supplier_name  VARCHAR(200),
    supplier_id    VARCHAR(100),
    bid_amount     DECIMAL(15,2),
    bid_content    TEXT,
    legal_person   VARCHAR(50),
    shareholders   TEXT,
    is_winner      BOOLEAN DEFAULT FALSE,
    bid_date       DATE,
    created_at     TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE procurement_bid IS '投标记录表（招采稽核-场景3/4）';
COMMENT ON COLUMN procurement_bid.bid_content IS '投标文件摘要/关键词，用于相似度比对';
COMMENT ON COLUMN procurement_bid.shareholders IS '股东信息（JSON数组）';

CREATE TABLE IF NOT EXISTS supplier_relation (
    id                   BIGSERIAL PRIMARY KEY,
    supplier_id          VARCHAR(100),
    supplier_name        VARCHAR(200),
    related_person_name  VARCHAR(50),
    relation_type        VARCHAR(50),
    share_ratio          DECIMAL(5,2),
    internal_employee_id VARCHAR(100),
    created_at           TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE supplier_relation IS '供应商关联关系表（招采稽核-场景4利益输送）';
COMMENT ON COLUMN supplier_relation.relation_type IS '关联类型：股东/法人/董事/监事/高管';
COMMENT ON COLUMN supplier_relation.internal_employee_id IS '关联的内部员工ID，为空表示未发现关联';

CREATE INDEX IF NOT EXISTS idx_procurement_contract_org_code ON procurement_contract(org_code);
CREATE INDEX IF NOT EXISTS idx_procurement_contract_supplier ON procurement_contract(supplier_id);
CREATE INDEX IF NOT EXISTS idx_procurement_contract_date ON procurement_contract(contract_date DESC);
CREATE INDEX IF NOT EXISTS idx_procurement_bid_project ON procurement_bid(bid_project_id);
CREATE INDEX IF NOT EXISTS idx_supplier_relation_supplier ON supplier_relation(supplier_id);
