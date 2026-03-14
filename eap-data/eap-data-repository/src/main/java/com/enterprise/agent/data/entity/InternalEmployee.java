package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内部员工实体（对应 internal_employee 表）
 * 数据来源：EHR人员系统（EhrSystemAdapter 同步）
 */
@Data
@TableName("internal_employee")
public class InternalEmployee {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 工号（唯一标识） */
    @TableField("employee_id")
    private String employeeId;

    /** 姓名（用于与供应商法人/股东交叉比对） */
    @TableField("employee_name")
    private String employeeName;

    /** 所在部门 */
    @TableField("department")
    private String department;

    /** 职务/岗位 */
    @TableField("position")
    private String position;

    /** 联系电话 */
    @TableField("phone")
    private String phone;

    @TableField(value = "synced_at", fill = FieldFill.INSERT)
    private LocalDateTime syncedAt;
}
