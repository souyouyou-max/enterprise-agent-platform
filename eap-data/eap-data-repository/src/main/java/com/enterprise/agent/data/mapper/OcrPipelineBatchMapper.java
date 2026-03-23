package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.OcrPipelineBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * OCR 流水线批次 Mapper
 */
@Mapper
public interface OcrPipelineBatchMapper extends BaseMapper<OcrPipelineBatch> {

    @Select("SELECT * FROM ocr_pipeline_batch WHERE batch_no = #{batchNo}")
    OcrPipelineBatch findByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 查询指定状态且 updated_at 超过 N 秒未更新的批次（用于定时任务检测卡住的批次）。
     *
     * @param status     批次状态（如 OCR_PROCESSING）
     * @param staleSeconds 超时秒数
     */
    @Select("""
            SELECT * FROM ocr_pipeline_batch
            WHERE status = #{status}
              AND updated_at < (CURRENT_TIMESTAMP - NUMTODSINTERVAL(#{staleSeconds}, 'SECOND'))
            ORDER BY updated_at ASC
            """)
    List<OcrPipelineBatch> findStale(@Param("status") String status,
                                     @Param("staleSeconds") int staleSeconds);

    /** 按 app_code 查询批次列表（管理后台） */
    @Select("""
            SELECT * FROM ocr_pipeline_batch
            WHERE (#{appCode} IS NULL OR app_code = #{appCode})
              AND (#{status} IS NULL OR status = #{status})
            ORDER BY created_at DESC
            """)
    List<OcrPipelineBatch> findList(@Param("appCode") String appCode,
                                    @Param("status") String status);

    /** CAS 状态更新（仅在当前状态匹配时才更新，防止并发覆盖） */
    @Update("""
            UPDATE ocr_pipeline_batch
            SET status = #{newStatus}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = #{expectedStatus}
            """)
    int casStatus(@Param("id") Long id,
                  @Param("expectedStatus") String expectedStatus,
                  @Param("newStatus") String newStatus);

    /** 递增完成计数 + 更新状态 */
    @Update("""
            UPDATE ocr_pipeline_batch
            SET ocr_done_files = ocr_done_files + 1,
                status = #{newStatus},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    void incrementOcrDone(@Param("id") Long id, @Param("newStatus") String newStatus);

    @Update("""
            UPDATE ocr_pipeline_batch
            SET analysis_done_files = analysis_done_files + 1,
                status = #{newStatus},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    void incrementAnalysisDone(@Param("id") Long id, @Param("newStatus") String newStatus);
}
