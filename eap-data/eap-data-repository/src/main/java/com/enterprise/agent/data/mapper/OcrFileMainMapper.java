package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.OcrFileMain;
import org.apache.ibatis.annotations.Mapper;

/**
 * OCR主文件 Mapper
 * <p>
 * 仅继承 BaseMapper，所有查询/更新均通过 MyBatis-Plus LambdaWrapper 在 Service 层完成，
 * 避免散落的手写 SQL 难以维护。
 */
@Mapper
public interface OcrFileMainMapper extends BaseMapper<OcrFileMain> {
}
