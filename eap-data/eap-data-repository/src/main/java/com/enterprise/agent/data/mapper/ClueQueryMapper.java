package com.enterprise.agent.data.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 规则SQL查询 Mapper
 * 封装规则引擎所需的所有跨表联查 SQL，供四条审计规则调用。
 */
@Mapper
public interface ClueQueryMapper {

    /**
     * 大额未招标规则（UntenderedRule）
     * 查找付款金额超过阈值，且无对应招采流程记录的付款台账。
     * 关联逻辑：payment_record.contract_no = procurement_project.project_code
     */
    @Select("SELECT pr.contract_no, pr.supplier_name, pr.supplier_id, " +
            "       pr.payment_amount, pr.payment_date, pr.project_category, pr.payment_purpose " +
            "FROM payment_record pr " +
            "LEFT JOIN procurement_project pp " +
            "       ON pr.supplier_id = pp.supplier_id AND pr.contract_no = pp.project_code " +
            "WHERE pr.org_code = #{orgCode} " +
            "  AND pr.payment_amount > #{threshold} " +
            "  AND pp.id IS NULL " +
            "ORDER BY pr.payment_amount DESC")
    List<Map<String, Object>> findUntenderedPayments(
            @Param("orgCode") String orgCode,
            @Param("threshold") BigDecimal threshold);

    /**
     * 化整为零规则（SplitPurchaseRule）
     * 查找同一供应商在60天内存在多笔小额付款，累计金额超过阈值的情况。
     * 每笔单独金额需小于阈值（说明是故意拆分），累计超过阈值（规避招标门槛）。
     */
    @Select("SELECT supplier_name, supplier_id, " +
            "       COUNT(*) AS contract_count, " +
            "       SUM(payment_amount) AS total_amount, " +
            "       MIN(payment_date) AS earliest_date, " +
            "       MAX(payment_date) AS latest_date " +
            "FROM payment_record " +
            "WHERE org_code = #{orgCode} " +
            "  AND payment_date >= CURRENT_DATE - INTERVAL '60 days' " +
            "  AND payment_amount < #{singleThreshold} " +
            "GROUP BY supplier_name, supplier_id " +
            "HAVING COUNT(*) >= 2 AND SUM(payment_amount) > #{totalThreshold} " +
            "ORDER BY total_amount DESC")
    List<Map<String, Object>> findSplitPurchases(
            @Param("orgCode") String orgCode,
            @Param("singleThreshold") BigDecimal singleThreshold,
            @Param("totalThreshold") BigDecimal totalThreshold);

    /**
     * 围标串标规则（CollusiveBidRule）
     * 查找同一项目中，多个不同投标供应商拥有相同法定代表人的情况。
     * 通过 procurement_bid（招采系统投标记录）和 supplier_info（工商信息）交叉比对。
     */
    @Select("SELECT pb1.bid_project_id, pb1.project_name, " +
            "       pb1.supplier_name AS winner_supplier, pb1.supplier_id AS winner_id, " +
            "       pb2.supplier_name AS colluder_supplier, pb2.supplier_id AS colluder_id, " +
            "       si1.legal_person AS shared_legal_person, " +
            "       pb1.bid_amount AS winning_amount " +
            "FROM procurement_bid pb1 " +
            "JOIN procurement_bid pb2 " +
            "       ON pb1.bid_project_id = pb2.bid_project_id AND pb1.id < pb2.id " +
            "JOIN supplier_info si1 ON pb1.supplier_id = si1.supplier_id " +
            "JOIN supplier_info si2 ON pb2.supplier_id = si2.supplier_id " +
            "JOIN procurement_project pp ON pb1.bid_project_id = pp.project_code " +
            "WHERE pp.org_code = #{orgCode} " +
            "  AND si1.legal_person IS NOT NULL " +
            "  AND si1.legal_person = si2.legal_person " +
            "  AND pb1.is_winner = TRUE " +
            "ORDER BY pb1.bid_project_id")
    List<Map<String, Object>> findCollusiveBids(@Param("orgCode") String orgCode);

    /**
     * 利益冲突规则（ConflictOfInterestRule）- 法定代表人与内部员工同名
     * 查找中标供应商法定代表人与内部员工同名的情况。
     */
    @Select("SELECT DISTINCT si.supplier_id, si.supplier_name, si.legal_person, " +
            "       ie.employee_name, ie.department, ie.position, " +
            "       pp.project_name, pp.contract_amount, pp.project_code " +
            "FROM supplier_info si " +
            "JOIN procurement_project pp ON si.supplier_id = pp.supplier_id AND pp.org_code = #{orgCode} " +
            "JOIN internal_employee ie ON si.legal_person = ie.employee_name " +
            "ORDER BY pp.contract_amount DESC")
    List<Map<String, Object>> findLegalPersonConflicts(@Param("orgCode") String orgCode);

    /**
     * 利益冲突规则（ConflictOfInterestRule）- 供应商股东与内部员工同名
     * 查找中标供应商股东姓名中包含内部员工姓名的情况（JSON字符串模糊匹配）。
     */
    @Select("SELECT DISTINCT si.supplier_id, si.supplier_name, si.shareholders, " +
            "       ie.employee_name, ie.department, ie.position, " +
            "       pp.project_name, pp.contract_amount, pp.project_code " +
            "FROM supplier_info si " +
            "JOIN procurement_project pp ON si.supplier_id = pp.supplier_id AND pp.org_code = #{orgCode} " +
            "JOIN internal_employee ie ON si.shareholders LIKE '%' || ie.employee_name || '%' " +
            "WHERE si.legal_person != ie.employee_name OR si.legal_person IS NULL " +
            "ORDER BY pp.contract_amount DESC")
    List<Map<String, Object>> findShareholderConflicts(@Param("orgCode") String orgCode);
}
