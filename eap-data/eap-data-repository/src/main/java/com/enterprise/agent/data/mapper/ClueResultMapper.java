package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.ClueResult;
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
}
