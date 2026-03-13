package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.AgentSubTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AgentSubTask MyBatis-Plus Mapper
 */
@Mapper
public interface AgentSubTaskMapper extends BaseMapper<AgentSubTask> {

    @Select("SELECT * FROM agent_sub_task WHERE task_id = #{taskId} ORDER BY sequence ASC")
    List<AgentSubTask> findByTaskId(Long taskId);

    @Delete("DELETE FROM agent_sub_task WHERE task_id = #{taskId}")
    int deleteByTaskId(Long taskId);
}
