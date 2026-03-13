package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.ProcurementContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Arrays;
import java.util.List;

/**
 * 采购合同 Mapper
 * <p>
 * 内置 Mock 数据，覆盖场景1（大额未招标）、场景2（化整为零）的典型案例。
 */
@Mapper
public interface ProcurementContractMapper extends BaseMapper<ProcurementContract> {

    @Select("SELECT * FROM procurement_contract WHERE org_code = #{orgCode} AND deleted = 0 ORDER BY contract_date DESC")
    List<ProcurementContract> findByOrgCode(String orgCode);

    @Select("SELECT * FROM procurement_contract WHERE org_code = #{orgCode} AND has_zc_process = false " +
            "AND contract_amount >= #{thresholdAmount} AND deleted = 0 ORDER BY contract_amount DESC")
    List<ProcurementContract> findUntenderedAboveThreshold(String orgCode, java.math.BigDecimal thresholdAmount);

    @Select("SELECT * FROM procurement_contract WHERE org_code = #{orgCode} " +
            "AND contract_date >= #{startDate} AND deleted = 0 ORDER BY supplier_id, contract_date")
    List<ProcurementContract> findByOrgCodeAndDateRange(String orgCode, java.time.LocalDate startDate);

    /**
     * 获取内置 Mock 合同数据（覆盖场景1、场景2）
     * 场景1：3条大额合同，has_zc_process=false
     * 场景2：2组化整为零案例（同供应商30天内多笔合同累计超门槛）
     */
    static List<ProcurementContract> getMockContracts() {
        java.time.LocalDate now = java.time.LocalDate.now();

        // ---- 场景1：大额采购未招标（3条）----
        ProcurementContract c1 = new ProcurementContract();
        c1.setId(1L);
        c1.setOrgCode("ORG001");
        c1.setProjectName("企业ERP系统升级改造项目");
        c1.setSupplierName("北京科技发展有限公司");
        c1.setSupplierId("SUP001");
        c1.setContractAmount(new java.math.BigDecimal("680000.00"));
        c1.setPaymentAmount(new java.math.BigDecimal("680000.00"));
        c1.setContractDate(now.minusDays(45));
        c1.setPaymentDate(now.minusDays(30));
        c1.setHasZcProcess(false);
        c1.setProjectCategory("IT服务");
        c1.setDeleted(0);

        ProcurementContract c2 = new ProcurementContract();
        c2.setId(2L);
        c2.setOrgCode("ORG001");
        c2.setProjectName("办公楼安防监控系统采购");
        c2.setSupplierName("上海安防设备集团");
        c2.setSupplierId("SUP002");
        c2.setContractAmount(new java.math.BigDecimal("920000.00"));
        c2.setPaymentAmount(new java.math.BigDecimal("460000.00"));
        c2.setContractDate(now.minusDays(60));
        c2.setPaymentDate(now.minusDays(15));
        c2.setHasZcProcess(false);
        c2.setProjectCategory("工程建设");
        c2.setDeleted(0);

        ProcurementContract c3 = new ProcurementContract();
        c3.setId(3L);
        c3.setOrgCode("ORG001");
        c3.setProjectName("数据中心运维服务外包");
        c3.setSupplierName("深圳云服务有限公司");
        c3.setSupplierId("SUP003");
        c3.setContractAmount(new java.math.BigDecimal("530000.00"));
        c3.setPaymentAmount(new java.math.BigDecimal("530000.00"));
        c3.setContractDate(now.minusDays(20));
        c3.setPaymentDate(null);
        c3.setHasZcProcess(false);
        c3.setProjectCategory("IT服务");
        c3.setDeleted(0);

        // ---- 场景2：化整为零案例A（供应商SUP004，30天内3笔IT服务合同累计49.8万）----
        ProcurementContract c4 = new ProcurementContract();
        c4.setId(4L);
        c4.setOrgCode("ORG001");
        c4.setProjectName("系统运维支持服务（一期）");
        c4.setSupplierName("广州软件技术有限公司");
        c4.setSupplierId("SUP004");
        c4.setContractAmount(new java.math.BigDecimal("168000.00"));
        c4.setPaymentAmount(new java.math.BigDecimal("168000.00"));
        c4.setContractDate(now.minusDays(28));
        c4.setPaymentDate(now.minusDays(25));
        c4.setHasZcProcess(false);
        c4.setProjectCategory("IT服务");
        c4.setDeleted(0);

        ProcurementContract c5 = new ProcurementContract();
        c5.setId(5L);
        c5.setOrgCode("ORG001");
        c5.setProjectName("系统运维支持服务（二期）");
        c5.setSupplierName("广州软件技术有限公司");
        c5.setSupplierId("SUP004");
        c5.setContractAmount(new java.math.BigDecimal("165000.00"));
        c5.setPaymentAmount(new java.math.BigDecimal("165000.00"));
        c5.setContractDate(now.minusDays(18));
        c5.setPaymentDate(now.minusDays(15));
        c5.setHasZcProcess(false);
        c5.setProjectCategory("IT服务");
        c5.setDeleted(0);

        ProcurementContract c6 = new ProcurementContract();
        c6.setId(6L);
        c6.setOrgCode("ORG001");
        c6.setProjectName("系统运维支持服务（三期）");
        c6.setSupplierName("广州软件技术有限公司");
        c6.setSupplierId("SUP004");
        c6.setContractAmount(new java.math.BigDecimal("165000.00"));
        c6.setPaymentAmount(null);
        c6.setContractDate(now.minusDays(5));
        c6.setPaymentDate(null);
        c6.setHasZcProcess(false);
        c6.setProjectCategory("IT服务");
        c6.setDeleted(0);

        // ---- 场景2：化整为零案例B（供应商SUP005，30天内2笔办公用品合同累计48万）----
        ProcurementContract c7 = new ProcurementContract();
        c7.setId(7L);
        c7.setOrgCode("ORG001");
        c7.setProjectName("办公用品及耗材采购（上半月）");
        c7.setSupplierName("北京办公商贸有限公司");
        c7.setSupplierId("SUP005");
        c7.setContractAmount(new java.math.BigDecimal("240000.00"));
        c7.setPaymentAmount(new java.math.BigDecimal("240000.00"));
        c7.setContractDate(now.minusDays(22));
        c7.setPaymentDate(now.minusDays(20));
        c7.setHasZcProcess(false);
        c7.setProjectCategory("办公用品");
        c7.setDeleted(0);

        ProcurementContract c8 = new ProcurementContract();
        c8.setId(8L);
        c8.setOrgCode("ORG001");
        c8.setProjectName("办公用品及耗材采购（下半月）");
        c8.setSupplierName("北京办公商贸有限公司");
        c8.setSupplierId("SUP005");
        c8.setContractAmount(new java.math.BigDecimal("238000.00"));
        c8.setPaymentAmount(null);
        c8.setContractDate(now.minusDays(8));
        c8.setPaymentDate(null);
        c8.setHasZcProcess(false);
        c8.setProjectCategory("办公用品");
        c8.setDeleted(0);

        return Arrays.asList(c1, c2, c3, c4, c5, c6, c7, c8);
    }
}
