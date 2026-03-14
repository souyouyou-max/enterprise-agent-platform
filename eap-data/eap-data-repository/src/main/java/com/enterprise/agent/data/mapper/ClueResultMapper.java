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

    @Select("SELECT * FROM clue_result WHERE org_code = #{orgCode} ORDER BY created_at DESC")
    List<ClueResult> findByOrgCode(@Param("orgCode") String orgCode);

    @Select("SELECT * FROM clue_result WHERE org_code = #{orgCode} AND status = 'PENDING' ORDER BY created_at DESC")
    List<ClueResult> findPendingByOrgCode(@Param("orgCode") String orgCode);

    @Delete("DELETE FROM clue_result WHERE org_code = #{orgCode} AND status = 'PENDING'")
    int deletePendingByOrgCode(@Param("orgCode") String orgCode);
}
