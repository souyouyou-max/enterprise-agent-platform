package com.sinosig.aip.engine.rag.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinosig.aip.engine.rag.knowledge.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档存储接口（MyBatis-Plus 自动实现 CRUD）
 * 生产环境可扩展为向 Milvus / PGVector 写入
 */
@Mapper
public interface KnowledgeRepository extends BaseMapper<KnowledgeDocument> {
}
