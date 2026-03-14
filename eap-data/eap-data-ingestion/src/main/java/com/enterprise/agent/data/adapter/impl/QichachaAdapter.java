package com.enterprise.agent.data.adapter.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.agent.data.adapter.DataSourceAdapter;
import com.enterprise.agent.data.entity.SupplierInfo;
import com.enterprise.agent.data.mapper.SupplierInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 企查查/天眼查适配器（Mock）
 * 模拟从企查查拉取供应商工商信息，写入 supplier_info 表。
 *
 * <p>关键数据（设计规则触发场景）：
 * <ul>
 *   <li>SUP001、SUP006：法定代表人均为"王磊"（围标串标证据）</li>
 *   <li>SUP004：股东"张明"（持股40%）与内部员工EMP001张明重名（利益冲突）</li>
 *   <li>SUP005：法定代表人"赵芳"与内部员工EMP010赵芳重名（利益冲突）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QichachaAdapter implements DataSourceAdapter {

    private final SupplierInfoMapper supplierInfoMapper;

    @Override
    public String getSourceName() {
        return "企查查";
    }

    @Override
    @Transactional
    public void syncData() {
        log.info("[{}] 开始同步供应商工商信息...", getSourceName());

        supplierInfoMapper.delete(new LambdaQueryWrapper<>());

        LocalDateTime syncTime = LocalDateTime.now();
        List<SupplierInfo> suppliers = buildSuppliers(syncTime);
        suppliers.forEach(supplierInfoMapper::insert);

        log.info("[{}] 同步完成：写入{}条供应商工商信息", getSourceName(), suppliers.size());
    }

    private List<SupplierInfo> buildSuppliers(LocalDateTime syncTime) {
        // SUP001：北京科技发展有限公司，法人王磊（与SUP006同一法人，围标！）
        SupplierInfo s1 = new SupplierInfo();
        s1.setSupplierId("SUP001");
        s1.setSupplierName("北京科技发展有限公司");
        s1.setLegalPerson("王磊");
        s1.setRegisteredCapital(new BigDecimal("500.00"));
        s1.setShareholders("[{\"name\":\"王磊\",\"ratio\":51},{\"name\":\"赵军\",\"ratio\":49}]");
        s1.setBusinessScope("计算机软件开发；信息技术服务；系统集成");
        s1.setRiskLevel("LOW");
        s1.setSyncedAt(syncTime);

        // SUP002：上海安防设备集团，正常供应商
        SupplierInfo s2 = new SupplierInfo();
        s2.setSupplierId("SUP002");
        s2.setSupplierName("上海安防设备集团");
        s2.setLegalPerson("陈波");
        s2.setRegisteredCapital(new BigDecimal("2000.00"));
        s2.setShareholders("[{\"name\":\"陈波\",\"ratio\":60},{\"name\":\"林静\",\"ratio\":40}]");
        s2.setBusinessScope("安防设备制造、销售及安装；弱电工程施工");
        s2.setRiskLevel("LOW");
        s2.setSyncedAt(syncTime);

        // SUP003：深圳云服务有限公司，正常供应商
        SupplierInfo s3 = new SupplierInfo();
        s3.setSupplierId("SUP003");
        s3.setSupplierName("深圳云服务有限公司");
        s3.setLegalPerson("刘伟");
        s3.setRegisteredCapital(new BigDecimal("300.00"));
        s3.setShareholders("[{\"name\":\"刘伟\",\"ratio\":70},{\"name\":\"周涛\",\"ratio\":30}]");
        s3.setBusinessScope("云计算服务；数据中心运维；网络安全");
        s3.setRiskLevel("MEDIUM");
        s3.setSyncedAt(syncTime);

        // SUP004：广州软件技术有限公司，股东"张明"持股40%（与内部员工EMP001重名，利益冲突！）
        SupplierInfo s4 = new SupplierInfo();
        s4.setSupplierId("SUP004");
        s4.setSupplierName("广州软件技术有限公司");
        s4.setLegalPerson("宋海");
        s4.setRegisteredCapital(new BigDecimal("200.00"));
        s4.setShareholders("[{\"name\":\"宋海\",\"ratio\":60},{\"name\":\"张明\",\"ratio\":40}]");
        s4.setBusinessScope("软件开发；系统运维；技术咨询");
        s4.setRiskLevel("LOW");
        s4.setSyncedAt(syncTime);

        // SUP005：北京办公商贸有限公司，法人"赵芳"（与内部员工EMP010重名，利益冲突！）
        SupplierInfo s5 = new SupplierInfo();
        s5.setSupplierId("SUP005");
        s5.setSupplierName("北京办公商贸有限公司");
        s5.setLegalPerson("赵芳");
        s5.setRegisteredCapital(new BigDecimal("100.00"));
        s5.setShareholders("[{\"name\":\"赵芳\",\"ratio\":80},{\"name\":\"钱进\",\"ratio\":20}]");
        s5.setBusinessScope("办公用品批发零售；文具耗材销售");
        s5.setRiskLevel("LOW");
        s5.setSyncedAt(syncTime);

        // SUP006：天津贸易科技有限公司，法人"王磊"（与SUP001同一法人，围标！）
        SupplierInfo s6 = new SupplierInfo();
        s6.setSupplierId("SUP006");
        s6.setSupplierName("天津贸易科技有限公司");
        s6.setLegalPerson("王磊");
        s6.setRegisteredCapital(new BigDecimal("150.00"));
        s6.setShareholders("[{\"name\":\"王磊\",\"ratio\":70},{\"name\":\"刘强\",\"ratio\":30}]");
        s6.setBusinessScope("软件开发；信息技术咨询；系统集成");
        s6.setRiskLevel("LOW");
        s6.setSyncedAt(syncTime);

        return Arrays.asList(s1, s2, s3, s4, s5, s6);
    }
}
