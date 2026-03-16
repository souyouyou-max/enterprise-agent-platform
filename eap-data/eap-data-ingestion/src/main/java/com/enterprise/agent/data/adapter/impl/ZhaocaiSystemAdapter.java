package com.enterprise.agent.data.adapter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.agent.data.adapter.DataSourceAdapter;
import com.enterprise.agent.data.entity.ProcurementBid;
import com.enterprise.agent.data.entity.ProcurementProject;
import com.enterprise.agent.data.mapper.ProcurementBidMapper;
import com.enterprise.agent.data.mapper.ProcurementProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 招采系统适配器（Mock）
 * 模拟从招采系统拉取招标项目数据，写入 procurement_project 表；
 * 同时写入 procurement_bid 表（含竞标记录），供围标串标规则使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZhaocaiSystemAdapter implements DataSourceAdapter {

    private final ProcurementProjectMapper projectMapper;
    private final ProcurementBidMapper bidMapper;

    @Override
    public String getSourceName() {
        return "招采系统";
    }

    @Override
    @Transactional
    public void syncData() {
        log.info("[{}] 开始同步招标项目数据...", getSourceName());

        // 清除旧数据（幂等同步）
        projectMapper.delete(new LambdaQueryWrapper<>());
        bidMapper.delete(new LambdaQueryWrapper<>());

        LocalDate now = LocalDate.now();
        LocalDateTime syncTime = LocalDateTime.now();

        // ---- 5条招标项目记录 ----
        List<ProcurementProject> projects = buildProjects(now, syncTime);
        projects.forEach(projectMapper::insert);

        // ---- 投标记录（含围标场景：BID-2024-001 两家供应商同一法人） ----
        List<ProcurementBid> bids = buildBids(now);
        bids.forEach(b -> {
            b.setCreatedAt(LocalDateTime.now());
            bidMapper.insert(b);
        });

        log.info("[{}] 同步完成：写入{}条招标项目，{}条投标记录", getSourceName(), projects.size(), bids.size());
    }

    private List<ProcurementProject> buildProjects(LocalDate now, LocalDateTime syncTime) {
        // P1：ERP项目 - 公开招标，流程完整
        ProcurementProject p1 = new ProcurementProject();
        p1.setProjectCode("BID-2024-001");
        p1.setProjectName("企业ERP系统升级改造项目");
        p1.setOrgCode("ORG001");
        p1.setContractAmount(new BigDecimal("680000.00"));
        p1.setBidMethod("公开招标");
        p1.setSupplierName("北京科技发展有限公司");
        p1.setSupplierId("SUP001");
        p1.setProjectDate(now.minusDays(45));
        p1.setHasBidProcess(true);
        p1.setSyncedAt(syncTime);

        // P2：安防项目 - 公开招标，流程完整
        ProcurementProject p2 = new ProcurementProject();
        p2.setProjectCode("BID-2024-002");
        p2.setProjectName("办公楼安防监控系统采购");
        p2.setOrgCode("ORG001");
        p2.setContractAmount(new BigDecimal("920000.00"));
        p2.setBidMethod("公开招标");
        p2.setSupplierName("上海安防设备集团");
        p2.setSupplierId("SUP002");
        p2.setProjectDate(now.minusDays(60));
        p2.setHasBidProcess(true);
        p2.setSyncedAt(syncTime);

        // P3：IT外包 - 直接采购，无招标流程（但对应的付款 contract_no 为 CNT-2024-003，两者不匹配，触发未招标规则）
        ProcurementProject p3 = new ProcurementProject();
        p3.setProjectCode("BID-2024-003");
        p3.setProjectName("数据中心运维服务外包（正式合同）");
        p3.setOrgCode("ORG001");
        p3.setContractAmount(new BigDecimal("530000.00"));
        p3.setBidMethod("直接采购");
        p3.setSupplierName("深圳云服务有限公司");
        p3.setSupplierId("SUP003");
        p3.setProjectDate(now.minusDays(20));
        p3.setHasBidProcess(false);
        p3.setSyncedAt(syncTime);

        // P4：广州软件 - 公开招标，作为化整为零的背景项目
        ProcurementProject p4 = new ProcurementProject();
        p4.setProjectCode("BID-2024-004");
        p4.setProjectName("业务系统开发及运维服务框架协议");
        p4.setOrgCode("ORG001");
        p4.setContractAmount(new BigDecimal("500000.00"));
        p4.setBidMethod("竞争性谈判");
        p4.setSupplierName("广州软件技术有限公司");
        p4.setSupplierId("SUP004");
        p4.setProjectDate(now.minusDays(90));
        p4.setHasBidProcess(true);
        p4.setSyncedAt(syncTime);

        // P5：北京办公 - 公开招标
        ProcurementProject p5 = new ProcurementProject();
        p5.setProjectCode("BID-2024-005");
        p5.setProjectName("办公用品年度框架采购协议");
        p5.setOrgCode("ORG001");
        p5.setContractAmount(new BigDecimal("480000.00"));
        p5.setBidMethod("公开招标");
        p5.setSupplierName("北京办公商贸有限公司");
        p5.setSupplierId("SUP005");
        p5.setProjectDate(now.minusDays(95));
        p5.setHasBidProcess(true);
        p5.setSyncedAt(syncTime);

        return Arrays.asList(p1, p2, p3, p4, p5);
    }

    private List<ProcurementBid> buildBids(LocalDate now) {
        // ---- 围标场景：BID-2024-001 两家供应商（SUP001 和 SUP006）法定代表人均为"王磊" ----

        // B1：SUP001 中标
        ProcurementBid b1 = new ProcurementBid();
        b1.setBidProjectId("BID-2024-001");
        b1.setProjectName("企业ERP系统升级改造项目");
        b1.setSupplierName("北京科技发展有限公司");
        b1.setSupplierId("SUP001");
        b1.setBidAmount(new BigDecimal("680000.00"));
        b1.setBidContent("提供ERP系统升级、数据迁移及3年运维服务");
        b1.setLegalPerson("王磊");
        b1.setShareholders("[{\"name\":\"王磊\",\"ratio\":51},{\"name\":\"赵军\",\"ratio\":49}]");
        b1.setIsWinner(true);
        b1.setBidDate(now.minusDays(55));
        b1.setBidDocumentUrl("http://mock-zhaocai.internal/files/SUP001-erp-bid.pdf");
        b1.setBidDocumentText(
                "北京科技发展有限公司 企业ERP系统升级改造项目 投标文件\n\n" +
                "一、技术方案\n" +
                "本公司拟采用SAP ERP系统作为核心平台，对现有系统进行全面升级改造。" +
                "技术架构采用微服务架构，基于Spring Boot 3.x框架开发，支持高并发、高可用部署。" +
                "数据迁移方案：采用ETL工具将历史数据从旧系统迁移至新系统，保证数据完整性及一致性。" +
                "实施周期分为三个阶段：需求调研（1个月）、系统开发（3个月）、测试上线（2个月）。\n\n" +
                "二、价格明细\n" +
                "软件开发费用：280,000元\n" +
                "系统集成费用：120,000元\n" +
                "数据迁移费用：80,000元\n" +
                "3年运维服务费：200,000元\n" +
                "合计：680,000元\n\n" +
                "三、项目团队\n" +
                "项目经理：张明，拥有10年ERP实施经验，PMP认证。\n" +
                "技术负责人：李华，精通SAP及Java开发，高级工程师。\n" +
                "运维工程师：王强，具备5年企业级系统运维经验。\n\n" +
                "四、售后保障\n" +
                "提供7×24小时技术支持，响应时间不超过2小时。" +
                "系统上线后保修期12个月，提供免费升级及补丁服务。" +
                "设立专属客服热线，确保用户问题得到及时解决。\n\n" +
                "五、资质证明\n" +
                "软件企业认定证书、ISO9001质量管理体系认证、高新技术企业证书。"
        );

        // B2：SUP006 陪标（同一法定代表人"王磊"，围标！文本与B1高度相似）
        ProcurementBid b2 = new ProcurementBid();
        b2.setBidProjectId("BID-2024-001");
        b2.setProjectName("企业ERP系统升级改造项目");
        b2.setSupplierName("天津贸易科技有限公司");
        b2.setSupplierId("SUP006");
        b2.setBidAmount(new BigDecimal("750000.00"));
        b2.setBidContent("ERP系统全面升级实施方案");
        b2.setLegalPerson("王磊"); // 与 SUP001 同一法人，围标证据！
        b2.setShareholders("[{\"name\":\"王磊\",\"ratio\":70},{\"name\":\"刘强\",\"ratio\":30}]");
        b2.setIsWinner(false);
        b2.setBidDate(now.minusDays(55));
        b2.setBidDocumentUrl("http://mock-zhaocai.internal/files/SUP006-erp-bid.pdf");
        b2.setBidDocumentText(
                "天津贸易科技有限公司 企业ERP系统升级改造项目 投标文件\n\n" +
                "一、技术方案\n" +
                "本公司拟采用SAP ERP系统作为核心平台，对现有系统进行全面升级改造。" +
                "技术架构采用微服务架构，基于Spring Boot 3.x框架开发，支持高并发、高可用部署。" +
                "数据迁移方案：采用ETL工具将历史数据从旧系统迁移至新系统，保证数据完整性及一致性。" +
                "实施周期分为三个阶段：需求调研（1个月）、系统开发（3个月）、测试上线（2个月）。\n\n" +
                "二、价格明细\n" +
                "软件开发费用：308,000元\n" +
                "系统集成费用：132,000元\n" +
                "数据迁移费用：88,000元\n" +
                "3年运维服务费：222,000元\n" +
                "合计：750,000元\n\n" +
                "三、项目团队\n" +
                "项目经理：张明，拥有10年ERP实施经验，PMP认证。\n" +
                "技术负责人：李华，精通SAP及Java开发，高级工程师。\n" +
                "运维工程师：王强，具备5年企业级系统运维经验。\n\n" +
                "四、售后保障\n" +
                "提供7×24小时技术支持，响应时间不超过2小时。" +
                "系统上线后保修期12个月，提供免费升级及补丁服务。" +
                "设立专属客服热线，确保用户问题得到及时解决。\n\n" +
                "五、资质证明\n" +
                "软件企业认定证书、ISO9001质量管理体系认证、高新技术企业证书。"
        );

        // B3：BID-2024-002 独立正常投标记录
        ProcurementBid b3 = new ProcurementBid();
        b3.setBidProjectId("BID-2024-002");
        b3.setProjectName("办公楼安防监控系统采购");
        b3.setSupplierName("上海安防设备集团");
        b3.setSupplierId("SUP002");
        b3.setBidAmount(new BigDecimal("920000.00"));
        b3.setBidContent("提供全套安防监控设备及安装调试服务");
        b3.setLegalPerson("陈波");
        b3.setShareholders("[{\"name\":\"陈波\",\"ratio\":60},{\"name\":\"林静\",\"ratio\":40}]");
        b3.setIsWinner(true);
        b3.setBidDate(now.minusDays(65));
        b3.setBidDocumentUrl("http://mock-zhaocai.internal/files/SUP002-security-bid.docx");

        return Arrays.asList(b1, b2, b3);
    }
}
