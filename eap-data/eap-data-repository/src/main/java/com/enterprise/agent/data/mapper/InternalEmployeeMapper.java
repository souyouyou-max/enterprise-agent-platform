package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.InternalEmployee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 内部员工 Mapper
 */
@Mapper
public interface InternalEmployeeMapper extends BaseMapper<InternalEmployee> {

    @Select("SELECT * FROM internal_employee ORDER BY employee_id")
    List<InternalEmployee> findAll();
}
