package com.sinosig.aip.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinosig.aip.data.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AgentTask MyBatis-Plus Mapper
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    @Select("SELECT * FROM agent_task WHERE status = #{status} ORDER BY created_at DESC")
    List<AgentTask> findByStatus(String status);

    @Select("SELECT * FROM agent_task ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<AgentTask> findPage(long offset, long limit);

    @Select("SELECT COUNT(*) FROM agent_task")
    long countAll();
}
