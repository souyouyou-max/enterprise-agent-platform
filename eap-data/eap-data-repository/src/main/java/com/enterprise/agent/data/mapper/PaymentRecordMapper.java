package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 付款台账 Mapper
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    @Select("SELECT * FROM payment_record WHERE org_code = #{orgCode} ORDER BY payment_date DESC")
    List<PaymentRecord> findByOrgCode(@Param("orgCode") String orgCode);
}
