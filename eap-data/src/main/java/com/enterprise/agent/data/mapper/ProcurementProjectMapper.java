package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.ProcurementProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 招标项目 Mapper
 */
@Mapper
public interface ProcurementProjectMapper extends BaseMapper<ProcurementProject> {

    @Select("SELECT * FROM procurement_project WHERE org_code = #{orgCode} ORDER BY project_date DESC")
    List<ProcurementProject> findByOrgCode(@Param("orgCode") String orgCode);

    @Select("SELECT * FROM procurement_project WHERE project_code = #{projectCode}")
    ProcurementProject findByProjectCode(@Param("projectCode") String projectCode);
}
