package com.enterprise.agent.dataservice.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档实体（对应 knowledge_document 表）
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文档标题 */
    @TableField("title")
    private String title;

    /** 文档正文内容 */
    @TableField("content")
    private String content;

    /** 文档分类（如：HR / 财务 / 法务 / 产品） */
    @TableField("category")
    private String category;

    /** 文本向量，JSON 格式存储（float 数组） */
    @TableField("embedding")
    private String embedding;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
