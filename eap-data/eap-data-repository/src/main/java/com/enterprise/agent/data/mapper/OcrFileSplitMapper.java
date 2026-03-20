package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.OcrFileSplit;
import org.apache.ibatis.annotations.Mapper;

/**
 * OCR拆分文件 Mapper
 * <p>
 * 仅继承 BaseMapper，所有查询/更新均通过 MyBatis-Plus LambdaWrapper 在 Service 层完成。
 */
@Mapper
public interface OcrFileSplitMapper extends BaseMapper<OcrFileSplit> {
}
