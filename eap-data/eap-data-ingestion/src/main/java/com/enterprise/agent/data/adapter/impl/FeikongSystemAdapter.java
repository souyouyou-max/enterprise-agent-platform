package com.enterprise.agent.data.adapter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.agent.data.adapter.DataSourceAdapter;
import com.enterprise.agent.data.entity.PaymentRecord;
import com.enterprise.agent.data.mapper.PaymentRecordMapper;
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
 * 费控系统适配器（Mock）
 * 模拟从费控系统拉取付款台账，写入 payment_record 表。
 *
 * <p>Mock数据场景设计：
 * <ul>
 *   <li>PAY-001/002：合法付款（合同号能关联到招采系统），不触发规则</li>
 *   <li>PAY-003：大额付款（53万），合同号 CNT-2024-003 无法关联到招采系统 → 触发未招标规则</li>
 *   <li>PAY-004/005/006：SUP004 三笔化整为零付款（累计50.8万 > 50万门槛） → 触发化整为零规则</li>
 *   <li>PAY-007/008：SUP005 两笔化整为零付款（累计50.5万 > 50万门槛） → 触发化整为零规则</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeikongSystemAdapter implements DataSourceAdapter {

    private final PaymentRecordMapper paymentRecordMapper;

    @Override
    public String getSourceName() {
        return "费控系统";
    }

    @Override
    @Transactional
    public void syncData() {
        log.info("[{}] 开始同步付款台账数据...", getSourceName());

        paymentRecordMapper.delete(new LambdaQueryWrapper<>());

        LocalDate now = LocalDate.now();
        LocalDateTime syncTime = LocalDateTime.now();

        List<PaymentRecord> records = buildPaymentRecords(now, syncTime);
        records.forEach(paymentRecordMapper::insert);

        log.info("[{}] 同步完成：写入{}条付款台账记录", getSourceName(), records.size());
    }

    private List<PaymentRecord> buildPaymentRecords(LocalDate now, LocalDateTime syncTime) {

        // PAY-001：合法付款，关联招采系统 BID-2024-001
        PaymentRecord r1 = new PaymentRecord();
        r1.setContractNo("BID-2024-001");
        r1.setOrgCode("ORG001");
        r1.setSupplierName("北京科技发展有限公司");
        r1.setSupplierId("SUP001");
        r1.setPaymentAmount(new BigDecimal("680000.00"));
        r1.setPaymentDate(now.minusDays(30));
        r1.setProjectCategory("IT服务");
        r1.setPaymentPurpose("ERP系统升级改造项目尾款");
        r1.setSyncedAt(syncTime);

        // PAY-002：合法付款，关联招采系统 BID-2024-002（部分付款）
        PaymentRecord r2 = new PaymentRecord();
        r2.setContractNo("BID-2024-002");
        r2.setOrgCode("ORG001");
        r2.setSupplierName("上海安防设备集团");
        r2.setSupplierId("SUP002");
        r2.setPaymentAmount(new BigDecimal("460000.00"));
        r2.setPaymentDate(now.minusDays(15));
        r2.setProjectCategory("工程建设");
        r2.setPaymentPurpose("安防监控系统采购首付款");
        r2.setSyncedAt(syncTime);

        // PAY-003：大额未招标 - 合同号 CNT-2024-003 在招采系统中不存在（与 BID-2024-003 不同），触发规则
        PaymentRecord r3 = new PaymentRecord();
        r3.setContractNo("CNT-2024-003");
        r3.setOrgCode("ORG001");
        r3.setSupplierName("深圳云服务有限公司");
        r3.setSupplierId("SUP003");
        r3.setPaymentAmount(new BigDecimal("530000.00"));
        r3.setPaymentDate(now.minusDays(10));
        r3.setProjectCategory("IT服务");
        r3.setPaymentPurpose("数据中心运维服务外包费用");
        r3.setSyncedAt(syncTime);

        // PAY-004/005/006：SUP004 化整为零（3笔，60天内，合计50.8万 > 50万）
        PaymentRecord r4 = new PaymentRecord();
        r4.setContractNo("CNT-2024-004A");
        r4.setOrgCode("ORG001");
        r4.setSupplierName("广州软件技术有限公司");
        r4.setSupplierId("SUP004");
        r4.setPaymentAmount(new BigDecimal("168000.00"));
        r4.setPaymentDate(now.minusDays(50));
        r4.setProjectCategory("IT服务");
        r4.setPaymentPurpose("系统运维支持服务（一期）");
        r4.setSyncedAt(syncTime);

        PaymentRecord r5 = new PaymentRecord();
        r5.setContractNo("CNT-2024-004B");
        r5.setOrgCode("ORG001");
        r5.setSupplierName("广州软件技术有限公司");
        r5.setSupplierId("SUP004");
        r5.setPaymentAmount(new BigDecimal("165000.00"));
        r5.setPaymentDate(now.minusDays(30));
        r5.setProjectCategory("IT服务");
        r5.setPaymentPurpose("系统运维支持服务（二期）");
        r5.setSyncedAt(syncTime);

        PaymentRecord r6 = new PaymentRecord();
        r6.setContractNo("CNT-2024-004C");
        r6.setOrgCode("ORG001");
        r6.setSupplierName("广州软件技术有限公司");
        r6.setSupplierId("SUP004");
        r6.setPaymentAmount(new BigDecimal("175000.00"));
        r6.setPaymentDate(now.minusDays(10));
        r6.setProjectCategory("IT服务");
        r6.setPaymentPurpose("系统运维支持服务（三期）");
        r6.setSyncedAt(syncTime);

        // PAY-007/008：SUP005 化整为零（2笔，60天内，合计50.5万 > 50万）
        PaymentRecord r7 = new PaymentRecord();
        r7.setContractNo("CNT-2024-005A");
        r7.setOrgCode("ORG001");
        r7.setSupplierName("北京办公商贸有限公司");
        r7.setSupplierId("SUP005");
        r7.setPaymentAmount(new BigDecimal("240000.00"));
        r7.setPaymentDate(now.minusDays(22));
        r7.setProjectCategory("办公用品");
        r7.setPaymentPurpose("办公用品及耗材采购（上半月）");
        r7.setSyncedAt(syncTime);

        PaymentRecord r8 = new PaymentRecord();
        r8.setContractNo("CNT-2024-005B");
        r8.setOrgCode("ORG001");
        r8.setSupplierName("北京办公商贸有限公司");
        r8.setSupplierId("SUP005");
        r8.setPaymentAmount(new BigDecimal("265000.00"));
        r8.setPaymentDate(now.minusDays(8));
        r8.setProjectCategory("办公用品");
        r8.setPaymentPurpose("办公用品及耗材采购（下半月）");
        r8.setSyncedAt(syncTime);

        return Arrays.asList(r1, r2, r3, r4, r5, r6, r7, r8);
    }
}
