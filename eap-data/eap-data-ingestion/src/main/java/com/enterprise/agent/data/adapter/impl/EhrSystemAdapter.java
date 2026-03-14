package com.enterprise.agent.data.adapter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.agent.data.adapter.DataSourceAdapter;
import com.enterprise.agent.data.entity.InternalEmployee;
import com.enterprise.agent.data.mapper.InternalEmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * EHR 人员系统适配器（Mock）
 * 模拟从EHR系统拉取员工信息，写入 internal_employee 表。
 *
 * <p>关键人员（用于利益冲突检测）：
 * <ul>
 *   <li>EMP001 张明（采购部）：是 SUP004 股东（持股40%）</li>
 *   <li>EMP008 王磊（采购部）：是 SUP001、SUP006 法定代表人，同时是围标两方的实际控制人</li>
 *   <li>EMP010 赵芳（行政部）：是 SUP005 法定代表人</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EhrSystemAdapter implements DataSourceAdapter {

    private final InternalEmployeeMapper employeeMapper;

    @Override
    public String getSourceName() {
        return "EHR人员系统";
    }

    @Override
    @Transactional
    public void syncData() {
        log.info("[{}] 开始同步员工信息数据...", getSourceName());

        employeeMapper.delete(new LambdaQueryWrapper<>());

        LocalDateTime syncTime = LocalDateTime.now();
        List<InternalEmployee> employees = buildEmployees(syncTime);
        employees.forEach(employeeMapper::insert);

        log.info("[{}] 同步完成：写入{}条员工记录", getSourceName(), employees.size());
    }

    private List<InternalEmployee> buildEmployees(LocalDateTime syncTime) {
        return Arrays.asList(
                emp("EMP001", "张明", "采购部", "采购经理", "13800001001", syncTime),   // SUP004股东（利益冲突）
                emp("EMP002", "李华", "财务部", "财务总监", "13800001002", syncTime),
                emp("EMP003", "王芳", "人事部", "HR主管", "13800001003", syncTime),
                emp("EMP004", "陈伟", "技术部", "IT主管", "13800001004", syncTime),
                emp("EMP005", "刘洋", "销售部", "销售总监", "13800001005", syncTime),
                emp("EMP006", "张伟", "法务部", "法务专员", "13800001006", syncTime),
                emp("EMP007", "李明", "运营部", "运营经理", "13800001007", syncTime),
                emp("EMP008", "王磊", "采购部", "采购专员", "13800001008", syncTime),   // SUP001/SUP006法人（利益冲突+围标）
                emp("EMP009", "孙建", "财务部", "出纳", "13800001009", syncTime),
                emp("EMP010", "赵芳", "行政部", "行政专员", "13800001010", syncTime)    // SUP005法人（利益冲突）
        );
    }

    private InternalEmployee emp(String id, String name, String dept, String pos, String phone, LocalDateTime syncTime) {
        InternalEmployee e = new InternalEmployee();
        e.setEmployeeId(id);
        e.setEmployeeName(name);
        e.setDepartment(dept);
        e.setPosition(pos);
        e.setPhone(phone);
        e.setSyncedAt(syncTime);
        return e;
    }
}
