package com.sinosig.aip.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinosig.aip.data.entity.ClueResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 疑点线索结果 Mapper
 */
@Mapper
public interface ClueResultMapper extends BaseMapper<ClueResult> {

    @Select("SELECT * FROM clue_result WHERE apply_code = #{applyCode} ORDER BY created_at DESC")
    List<ClueResult> findByApplyCode(@Param("applyCode") String applyCode);

    @Select("SELECT * FROM clue_result WHERE apply_code = #{applyCode} AND status = 'PENDING' ORDER BY created_at DESC")
    List<ClueResult> findPendingByApplyCode(@Param("applyCode") String applyCode);

    @Delete("DELETE FROM clue_result WHERE apply_code = #{applyCode} AND status = 'PENDING'")
    int deletePendingByApplyCode(@Param("applyCode") String applyCode);

    /**
     * 批量插入疑点线索（替代循环单条 insert，减少 DB 往返）。
     * MyBatis-Plus 的 BaseMapper 不内置批量插入，此处通过 XML 动态 SQL 实现。
     * 对应 XML：resources/mapper/ClueResultMapper.xml
     */
    int insertBatch(@Param("list") List<ClueResult> clues);
}
