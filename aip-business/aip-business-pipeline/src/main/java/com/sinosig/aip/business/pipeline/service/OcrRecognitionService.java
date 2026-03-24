package com.sinosig.aip.business.pipeline.service;

import com.sinosig.aip.data.entity.OcrFileMain;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * OCR 识别服务接口
 *
 * <h3>完整流程</h3>
 * <pre>
 * 调用方组装 {@link OcrRecognitionRequest}（含文件元信息 + 引擎请求体）
 *   ↓
 * recognize()
 *   ① createMain()          — 识别前先入库，status=PENDING
 *   ② markMainProcessing()  — 开始识别，status=PROCESSING
 *   ③ 调用引擎（大智部 / 正言多模态）
 *   ④-A 单图/无拆分        — saveDirectResult()，同时写 split 表 1 条，status→SUCCESS
 *   ④-B 多页/拆分          — saveSplits() + 逐页 saveSplitResult()，全部完成后聚合，status→SUCCESS
 *   ⑤ 异常                 — markMainFailed()，status→FAILED
 * </pre>
 *
 * <p>实现类：{@link impl.OcrRecognitionServiceImpl}
 */
public interface OcrRecognitionService {

    /**
     * 执行 OCR 识别并将结果入库。
     *
     * @param request 识别请求（文件元信息 + 引擎请求体），参见 {@link OcrRecognitionRequest}
     * @return 识别完成后的主文件实体（含 id、ocrResult、ocr_status 等）
     * @throws Exception 引擎调用异常或持久化异常（已自动标记主文件为 FAILED）
     */
    OcrFileMain recognize(OcrRecognitionRequest request) throws Exception;

    /**
     * 已在上层调用过识别引擎（或由其它入口得到引擎返回结果），只负责将结果落库。
     *
     * @param request 识别请求（文件元信息 + source 等），engineRequest 可为 null
     * @param engineResult 引擎返回的原始结果节点（格式由各入口保证）
     */
    OcrFileMain recognizeFromResult(OcrRecognitionRequest request, JsonNode engineResult) throws Exception;
}
