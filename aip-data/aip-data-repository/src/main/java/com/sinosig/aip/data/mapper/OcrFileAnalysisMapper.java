package com.sinosig.aip.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinosig.aip.data.entity.OcrFileAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 多模态语义分析结果 Mapper
 */
@Mapper
public interface OcrFileAnalysisMapper extends BaseMapper<OcrFileAnalysis> {

    /** 按主文件 ID 查询分析记录 */
    @Select("SELECT * FROM ocr_file_analysis WHERE main_id = #{mainId} ORDER BY created_at ASC")
    List<OcrFileAnalysis> findByMainId(@Param("mainId") Long mainId);

    /** 按批次号查询所有分析记录 */
    @Select("SELECT * FROM ocr_file_analysis WHERE batch_no = #{batchNo} ORDER BY created_at ASC")
    List<OcrFileAnalysis> findByBatchNo(@Param("batchNo") String batchNo);

    /** 统计批次中指定状态的记录数 */
    @Select("SELECT COUNT(*) FROM ocr_file_analysis WHERE batch_no = #{batchNo} AND status = #{status}")
    long countByBatchNoAndStatus(@Param("batchNo") String batchNo, @Param("status") String status);
}
