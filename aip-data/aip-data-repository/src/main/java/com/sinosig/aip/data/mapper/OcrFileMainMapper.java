package com.sinosig.aip.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinosig.aip.data.entity.OcrFileMain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * OCR主文件 Mapper
 * <p>
 * 仅继承 BaseMapper，所有查询/更新均通过 MyBatis-Plus LambdaWrapper 在 Service 层完成，
 * 避免散落的手写 SQL 难以维护。
 */
@Mapper
public interface OcrFileMainMapper extends BaseMapper<OcrFileMain> {

    /**
     * 批量插入主文件记录（替代循环单条 insert，减少 DB 往返）。
     * 调用前须由 {@code createMainBatch()} 预先填充 id / created_at / updated_at。
     * 对应 XML：resources/mapper/OcrFileMainMapper.xml
     */
    int insertBatch(@Param("list") List<OcrFileMain> mains);
}
