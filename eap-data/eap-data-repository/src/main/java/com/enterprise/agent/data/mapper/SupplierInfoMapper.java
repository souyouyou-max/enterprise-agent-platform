package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.SupplierInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 供应商工商信息 Mapper
 */
@Mapper
public interface SupplierInfoMapper extends BaseMapper<SupplierInfo> {

    @Select("SELECT * FROM supplier_info WHERE supplier_id = #{supplierId}")
    SupplierInfo findBySupplierId(@Param("supplierId") String supplierId);
}
