package com.sinosig.aip.data.service;

import com.sinosig.aip.data.entity.OcrFileMain;
import com.sinosig.aip.data.entity.OcrFileSplit;

import java.util.List;

/**
 * OCR文件数据服务接口
 *
 * <h3>整体流程</h3>
 * <pre>
 * ① 识别前：createMain()            建立主文件记录，status=PENDING
 * ② 开始识别：markMainProcessing()  status=PROCESSING
 *
 * 【单图/无拆分路径】—— 图片或单页文件，直接送识别引擎：
 *   ③ 识别完成 → saveDirectResult()   prompt + ocrResult 写主表 + 一条分片记录，status→SUCCESS
 *   ③ 识别失败 → markMainFailed()     status→FAILED
 *
 * 【多页/拆分路径】—— 多页PDF等大文件拆成若干子图后逐片识别：
 *   ③ 拆分完成 → saveSplits()         批量建分片记录，total_pages 写主表
 *   ④ 每片完成 → saveSplitResult()    prompt + ocrResult 写分片；全片完成后自动聚合主表→SUCCESS
 *   ④ 某片失败 → markSplitFailed()    主表→FAILED
 * </pre>
 *
 * <p>实现类：{@link com.sinosig.aip.data.service.impl.OcrFileDataServiceImpl}
 */
public interface OcrFileDataService {

    /** OCR 处理状态常量 */
    String STATUS_PENDING    = "PENDING";
    String STATUS_PROCESSING = "PROCESSING";
    String STATUS_SUCCESS    = "SUCCESS";
    String STATUS_FAILED     = "FAILED";

    // ── 主文件：生命周期 ────────────────────────────────────────

    /** 【步骤①】识别开始前创建主文件记录（status=PENDING），ID 由雪花算法自动填充 */
    OcrFileMain createMain(OcrFileMain main);

    /** 【步骤②】开始识别时推进状态为 PROCESSING */
    void markMainProcessing(Long id);

    /**
     * 【单图/无拆分 步骤③】整文件识别完成，写入结果，status→SUCCESS。
     * 同时在 split 表写入一条记录（split_index=0，file_path 复用主文件路径）以保持两表结构一致。
     */
    void saveDirectResult(OcrFileMain mainFile, String prompt, String ocrResult, String splitImageBase64);

    /** 标记主文件整体失败 */
    void markMainFailed(Long id, String errorMessage);

    // ── 主文件：查询 ────────────────────────────────────────────

    OcrFileMain findMainById(Long id);

    OcrFileMain findMainByBusinessNo(String businessNo);

    List<OcrFileMain> findMainByStatus(String status);

    List<OcrFileMain> findMainBySourceAndStatus(String source, String status);

    /**
     * OCR 主表列表查询（用于管理后台预览）。
     *
     * @param source 可选：来源（DAZHI_OCR / ZHENGYAN_MULTIMODAL），空则不过滤
     * @param status 可选：ocr_status（PENDING/PROCESSING/SUCCESS/FAILED），空则不过滤
     * @param limit 最大条数（建议小于等于 100）
     */
    List<OcrFileMain> findMainList(String source, String status, Integer limit);

    // ── 分片：生命周期（多页/拆分路径）─────────────────────────

    /** 【多页/拆分路径 步骤③】批量保存分片记录，推进主文件 status→PROCESSING */
    void saveSplits(Long mainId, List<OcrFileSplit> splits);

    /**
     * 【多页/拆分路径 步骤④】单分片识别完成，回写结果并触发聚合检查。
     * 所有分片 SUCCESS 后自动聚合主表 status→SUCCESS。
     */
    void saveSplitResult(Long splitId, Long mainId, String prompt, String ocrResult);

    /**
     * 回写分片级大模型/多模态识别结果（仅更新 {@code llm_result}，不修改 {@code ocr_result} 与 OCR 聚合逻辑）。
     */
    void saveSplitLlmResult(Long splitId, Long mainId, String llmResult);

    /** 标记单分片失败，同步将主文件标记为 FAILED */
    void markSplitFailed(Long splitId, Long mainId, String errorMessage);

    // ── 分片：查询 ──────────────────────────────────────────────

    List<OcrFileSplit> findSplitsByMainId(Long mainId);

    long countSplitsByStatus(Long mainId, String status);

    // ── 重试 ────────────────────────────────────────────────────

    /** 重置主文件及其分片，status→PENDING，用于重试场景 */
    void resetForReprocess(Long mainId);

    // ── 批次维度查询（Pipeline 使用）────────────────────────────

    /** 查询同一批次下的所有主文件记录 */
    List<OcrFileMain> findMainByBatchNo(String batchNo);

    /** 统计批次中指定 ocr_status 的文件数 */
    long countMainByBatchAndOcrStatus(String batchNo, String ocrStatus);

    /** 统计批次中指定 analysis_status 的文件数 */
    long countMainByBatchAndAnalysisStatus(String batchNo, String analysisStatus);

    // ── 多模态分析状态更新 ────────────────────────────────────────

    /** 将主文件的 analysis_status 更新为 PROCESSING */
    void markAnalysisProcessing(Long mainId);

    /** 将主文件的 analysis_status 更新为 SUCCESS */
    void markAnalysisSuccess(Long mainId);

    /** 将主文件的 analysis_status 更新为 FAILED */
    void markAnalysisFailed(Long mainId, String errorMessage);

    /** 将主文件的 analysis_status 更新为 SKIPPED（无图片内容时） */
    void markAnalysisSkipped(Long mainId);
}
