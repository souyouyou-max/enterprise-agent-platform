package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.ProcurementBid;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Arrays;
import java.util.List;

/**
 * 投标记录 Mapper
 * <p>
 * 内置 Mock 数据，覆盖场景3（围标串标）、场景4（利益输送）的典型案例。
 */
@Mapper
public interface ProcurementBidMapper extends BaseMapper<ProcurementBid> {

    @Select("SELECT * FROM procurement_bid WHERE bid_project_id = #{bidProjectId} ORDER BY bid_date")
    List<ProcurementBid> findByProjectId(String bidProjectId);

    @Select("SELECT * FROM procurement_bid WHERE is_winner = true ORDER BY bid_date DESC")
    List<ProcurementBid> findWinners();

    @Select("SELECT * FROM procurement_bid WHERE is_winner = true AND " +
            "(#{supplierId} IS NULL OR #{supplierId} = '' OR supplier_id = #{supplierId})")
    List<ProcurementBid> findWinnersBySupplier(String supplierId);

    /**
     * 获取内置 Mock 投标数据（覆盖场景3围标、场景4利益输送）
     * 场景3：项目 BID-PROJECT-001 有3家投标单位，投标文件高度相似，且股东存在重叠
     * 场景4：中标供应商中有2家的关联人员与内部员工同名
     */
    static List<ProcurementBid> getMockBids() {
        java.time.LocalDate bidDate = java.time.LocalDate.now().minusDays(30);

        // ---- 场景3：围标串标案例（项目：核心业务系统开发）----
        // 三家公司投标文件关键词高度重叠，且股东存在关联

        ProcurementBid b1 = new ProcurementBid();
        b1.setId(1L);
        b1.setBidProjectId("BID-PROJECT-001");
        b1.setProjectName("核心业务系统开发项目");
        b1.setSupplierName("甲成科技有限公司");
        b1.setSupplierId("SUP010");
        b1.setBidAmount(new java.math.BigDecimal("1500000.00"));
        b1.setBidContent("采用微服务架构 Spring Boot Spring Cloud 分布式部署 高可用 容灾备份 " +
                "DevOps CI/CD 自动化测试 敏捷开发 迭代交付 技术支持 运维保障 安全加密");
        b1.setLegalPerson("王建国");
        b1.setShareholders("[\"王建国\",\"刘海涛\",\"张伟明\"]");
        b1.setIsWinner(true);
        b1.setBidDate(bidDate);

        ProcurementBid b2 = new ProcurementBid();
        b2.setId(2L);
        b2.setBidProjectId("BID-PROJECT-001");
        b2.setProjectName("核心业务系统开发项目");
        b2.setSupplierName("乙腾软件股份有限公司");
        b2.setSupplierId("SUP011");
        b2.setBidAmount(new java.math.BigDecimal("1680000.00"));
        b2.setBidContent("采用微服务架构 Spring Boot Spring Cloud 分布式部署 高可用 容灾备份 " +
                "DevOps CI/CD 自动化测试 敏捷开发 迭代交付 技术支持 运维保障 数据安全");
        b2.setLegalPerson("刘海涛");
        b2.setShareholders("[\"刘海涛\",\"王建国\",\"陈小红\"]");
        b2.setIsWinner(false);
        b2.setBidDate(bidDate);

        ProcurementBid b3 = new ProcurementBid();
        b3.setId(3L);
        b3.setBidProjectId("BID-PROJECT-001");
        b3.setProjectName("核心业务系统开发项目");
        b3.setSupplierName("丙远信息技术有限公司");
        b3.setSupplierId("SUP012");
        b3.setBidAmount(new java.math.BigDecimal("1750000.00"));
        b3.setBidContent("采用微服务架构 Spring Boot Spring Cloud 分布式部署 高可用 容灾备份 " +
                "CI/CD 自动化测试 敏捷开发 迭代交付 运维服务 安全加密 性能优化");
        b3.setLegalPerson("张伟明");
        b3.setShareholders("[\"张伟明\",\"王建国\",\"李国强\"]");
        b3.setIsWinner(false);
        b3.setBidDate(bidDate);

        // ---- 场景4：利益输送案例（中标供应商股东/法人与内部员工同名）----

        ProcurementBid b4 = new ProcurementBid();
        b4.setId(4L);
        b4.setBidProjectId("BID-PROJECT-002");
        b4.setProjectName("办公设备批量采购项目");
        b4.setSupplierName("鑫达办公设备有限公司");
        b4.setSupplierId("SUP020");
        b4.setBidAmount(new java.math.BigDecimal("380000.00"));
        b4.setBidContent("品牌办公设备 联想 惠普 质保三年 送货安装 维修服务");
        b4.setLegalPerson("赵海波");
        b4.setShareholders("[\"赵海波\",\"陈志远\"]");
        b4.setIsWinner(true);
        b4.setBidDate(java.time.LocalDate.now().minusDays(20));

        ProcurementBid b5 = new ProcurementBid();
        b5.setId(5L);
        b5.setBidProjectId("BID-PROJECT-003");
        b5.setProjectName("年度保洁服务外包项目");
        b5.setSupplierName("洁美环境服务有限公司");
        b5.setSupplierId("SUP021");
        b5.setBidAmount(new java.math.BigDecimal("240000.00"));
        b5.setBidContent("专业保洁团队 设备先进 环保材料 全天候服务 突发应急");
        b5.setLegalPerson("林晓峰");
        b5.setShareholders("[\"林晓峰\",\"王丽华\"]");
        b5.setIsWinner(true);
        b5.setBidDate(java.time.LocalDate.now().minusDays(15));

        return Arrays.asList(b1, b2, b3, b4, b5);
    }

    /**
     * 获取内置 Mock 供应商关联关系数据（覆盖场景4利益输送）
     * 关联人员与内部员工同名：赵海波（采购部经理）、林晓峰（行政部副主任）
     */
    static List<com.enterprise.agent.data.entity.SupplierRelation> getMockSupplierRelations() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        com.enterprise.agent.data.entity.SupplierRelation r1 = new com.enterprise.agent.data.entity.SupplierRelation();
        r1.setId(1L);
        r1.setSupplierId("SUP020");
        r1.setSupplierName("鑫达办公设备有限公司");
        r1.setRelatedPersonName("赵海波");
        r1.setRelationType("法人");
        r1.setShareRatio(new java.math.BigDecimal("60.00"));
        r1.setInternalEmployeeId("EMP-20231");  // 关联内部员工：采购部经理赵海波
        r1.setCreatedAt(now);

        com.enterprise.agent.data.entity.SupplierRelation r2 = new com.enterprise.agent.data.entity.SupplierRelation();
        r2.setId(2L);
        r2.setSupplierId("SUP020");
        r2.setSupplierName("鑫达办公设备有限公司");
        r2.setRelatedPersonName("陈志远");
        r2.setRelationType("股东");
        r2.setShareRatio(new java.math.BigDecimal("40.00"));
        r2.setInternalEmployeeId(null);  // 未发现内部关联
        r2.setCreatedAt(now);

        com.enterprise.agent.data.entity.SupplierRelation r3 = new com.enterprise.agent.data.entity.SupplierRelation();
        r3.setId(3L);
        r3.setSupplierId("SUP021");
        r3.setSupplierName("洁美环境服务有限公司");
        r3.setRelatedPersonName("林晓峰");
        r3.setRelationType("法人");
        r3.setShareRatio(new java.math.BigDecimal("51.00"));
        r3.setInternalEmployeeId("EMP-10087");  // 关联内部员工：行政部副主任林晓峰
        r3.setCreatedAt(now);

        com.enterprise.agent.data.entity.SupplierRelation r4 = new com.enterprise.agent.data.entity.SupplierRelation();
        r4.setId(4L);
        r4.setSupplierId("SUP021");
        r4.setSupplierName("洁美环境服务有限公司");
        r4.setRelatedPersonName("王丽华");
        r4.setRelationType("股东");
        r4.setShareRatio(new java.math.BigDecimal("49.00"));
        r4.setInternalEmployeeId(null);
        r4.setCreatedAt(now);

        return java.util.Arrays.asList(r1, r2, r3, r4);
    }
}
