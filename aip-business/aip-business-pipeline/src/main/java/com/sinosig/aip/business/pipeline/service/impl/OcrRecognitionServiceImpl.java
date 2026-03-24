package com.sinosig.aip.business.pipeline.service.impl;

import com.sinosig.aip.business.pipeline.service.MultimodalService;
import com.sinosig.aip.business.pipeline.service.OcrRecognitionRequest;
import com.sinosig.aip.business.pipeline.service.OcrRecognitionService;
import com.sinosig.aip.data.entity.OcrFileMain;
import com.sinosig.aip.data.entity.OcrFileSplit;
import com.sinosig.aip.data.service.OcrFileDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * OCR 识别服务实现
 * <p>
 * 负责在识别前后插入持久化逻辑，将大智部 OCR 和正言多模态识别的结果统一入库。
 * 识别引擎调用委托给 {@link MultimodalService}，不重复实现引擎交互细节。
 *
 * <h3>流程示意</h3>
 * <pre>
 * recognize(request)
 *   ├─ createMain()             识别前先建主文件记录（PENDING）
 *   ├─ markMainProcessing()     推进状态
 *   ├─ [DAZHI_OCR]
 *   │   ├─ 单图 → dazhiOcrGeneral() → saveDirectResult()
 *   │   └─ 多页 → dazhiOcrGeneral() → saveSplits() + saveSplitResult() × N
 *   ├─ [ZHENGYAN_MULTIMODAL]
 *   │   ├─ 单图 → img2Text()    → saveDirectResult()
 *   │   └─ 多批 → img2Text()    → saveSplits() + saveSplitResult() × N
 *   └─ 异常 → markMainFailed()
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrRecognitionServiceImpl implements OcrRecognitionService {

    private static final String SOURCE_DAZHI       = "DAZHI_OCR";
    private static final String SOURCE_ZHENGYAN    = "ZHENGYAN_MULTIMODAL";

    private final MultimodalService    multimodalService;
    private final OcrFileDataService   ocrFileDataService;
    private final ObjectMapper         objectMapper;

    // ─────────────────────────────────────────────────────────
    // 公共入口
    // ─────────────────────────────────────────────────────────

    @Override
    public OcrFileMain recognize(OcrRecognitionRequest request) throws Exception {
        // ① 识别前先建主文件记录
        OcrFileMain main = buildMainEntity(request);
        ocrFileDataService.createMain(main);
        log.info("[OcrRecognition] 主文件记录创建, id={}, businessNo={}, source={}",
                main.getId(), main.getBusinessNo(), main.getSource());

        // ② 推进状态为 PROCESSING
        ocrFileDataService.markMainProcessing(main.getId());

        try {
            // ③ 调用识别引擎并持久化结果
            if (SOURCE_DAZHI.equalsIgnoreCase(request.getSource())) {
                recognizeByDazhi(main, request);
            } else if (SOURCE_ZHENGYAN.equalsIgnoreCase(request.getSource())) {
                recognizeByZhengyan(main, request);
            } else {
                throw new IllegalArgumentException("不支持的识别来源: " + request.getSource());
            }
        } catch (Exception e) {
            log.error("[OcrRecognition] 识别失败, id={}, error={}", main.getId(), e.getMessage(), e);
            ocrFileDataService.markMainFailed(main.getId(), truncate(e.getMessage(), 900));
            throw e;
        }

        // 返回最新主文件记录
        return ocrFileDataService.findMainById(main.getId());
    }

    @Override
    public OcrFileMain recognizeFromResult(OcrRecognitionRequest request, JsonNode engineResult) throws Exception {
        // 识别结果已由上层产生：这里只做“入库编排”与结果落库
        OcrFileMain main = buildMainEntity(request);
        ocrFileDataService.createMain(main);
        log.info("[OcrRecognition] 主文件记录创建(仅入库), id={}, businessNo={}, source={}",
                main.getId(), main.getBusinessNo(), main.getSource());

        ocrFileDataService.markMainProcessing(main.getId());

        try {
            if (SOURCE_DAZHI.equalsIgnoreCase(request.getSource())) {
                persistDazhiFromResult(main, request, engineResult);
            } else if (SOURCE_ZHENGYAN.equalsIgnoreCase(request.getSource())) {
                persistZhengyanFromResult(main, request, engineResult);
            } else {
                throw new IllegalArgumentException("不支持的识别来源: " + request.getSource());
            }
        } catch (Exception e) {
            log.error("[OcrRecognition] 仅入库失败, id={}, error={}", main.getId(), e.getMessage(), e);
            ocrFileDataService.markMainFailed(main.getId(), truncate(e.getMessage(), 900));
            throw e;
        }

        return ocrFileDataService.findMainById(main.getId());
    }

    // ─────────────────────────────────────────────────────────
    // 大智部 OCR
    // ─────────────────────────────────────────────────────────

    /**
     * 大智部 OCR 识别并入库。
     * <ul>
     *   <li>响应含 pages 数组且长度 > 1 → 多页路径：每页建一条 split 记录</li>
     *   <li>其他情况 → 单图路径：直接写主表 + 一条 split 记录（split_index=0）</li>
     * </ul>
     */
    private void recognizeByDazhi(OcrFileMain main, OcrRecognitionRequest request) throws Exception {
        ObjectNode engineReq = request.getEngineRequest() != null
                ? request.getEngineRequest()
                : objectMapper.createObjectNode();

        JsonNode result = multimodalService.dazhiOcrGeneral(engineReq);
        persistDazhiFromResult(main, request, result);
    }

    // ─────────────────────────────────────────────────────────
    // 正言多模态
    // ─────────────────────────────────────────────────────────

    /**
     * 正言多模态识别并入库。
     * <ul>
     *   <li>响应含 response 数组（多批次）且长度 > 1 → 多批路径：每批建一条 split 记录</li>
     *   <li>其他情况 → 单次路径</li>
     * </ul>
     */
    private void recognizeByZhengyan(OcrFileMain main, OcrRecognitionRequest request) throws Exception {
        ObjectNode engineReq = request.getEngineRequest() != null
                ? request.getEngineRequest()
                : objectMapper.createObjectNode();

        // 将 prompt 注入到 text 字段（若调用方未设置）
        String prompt = request.getPrompt();
        if (prompt != null && !prompt.isBlank() && !engineReq.has("text")) {
            engineReq.put("text", prompt);
        }

        ObjectNode result = multimodalService.img2Text(engineReq);
        persistZhengyanFromResult(main, request, result);
    }

    private void persistDazhiFromResult(OcrFileMain main, OcrRecognitionRequest request, JsonNode result) {
        String prompt = request.getPrompt();
        JsonNode pages = result == null ? null : result.path("pages");
        boolean isMultiPage = pages != null && pages.isArray() && pages.size() > 1;

        if (isMultiPage) {
            // 多页：为每页创建分片记录，逐页回写结果
            List<OcrFileSplit> splits = buildSplitSkeletons(main, pages.size());
            // 回填每页图片 base64（由 MultimodalService 在 pages[i] 中携带 imageBase64 字段）
            for (int i = 0; i < pages.size() && i < splits.size(); i++) {
                String imageBase64 = pages.get(i).path("imageBase64").asText("");
                splits.get(i).setImageBase64(imageBase64);
            }
            ocrFileDataService.saveSplits(main.getId(), splits);

            for (int i = 0; i < pages.size(); i++) {
                OcrFileSplit split = splits.get(i);
                JsonNode pageNode = pages.get(i);
                boolean pageOk = pageNode.path("success").asBoolean(false);
                String pageText = extractDazhiText(pageNode);

                if (pageOk) {
                    ocrFileDataService.saveSplitResult(split.getId(), main.getId(), prompt, pageText);
                } else {
                    String err = pageNode.path("message").asText("大智部页面识别失败");
                    ocrFileDataService.markSplitFailed(split.getId(), main.getId(), err);
                    // 主文件已被 markSplitFailed 标记失败，不继续处理剩余页
                    return;
                }
            }
        } else {
            // 单图/单页：直接写主表
            String content = result == null ? "" : result.path("content").asText("");
            if (content == null || content.isBlank()) {
                // 大智部单页（未走 attachments 拆分）时，content 可能不在 result 顶层
                content = extractDazhiText(result);
            }
            // 单页 base64：从 pages[0] 提取（attachments 拆分路径）
            String imageBase64 = "";
            if (result != null) {
                JsonNode pagesNode = result.path("pages");
                if (pagesNode.isArray() && pagesNode.size() > 0) {
                    imageBase64 = pagesNode.get(0).path("imageBase64").asText("");
                }
            }
            ocrFileDataService.saveDirectResult(main, prompt, content, imageBase64);
        }
    }

    private void persistZhengyanFromResult(OcrFileMain main, OcrRecognitionRequest request, JsonNode result) {
        String prompt = request.getPrompt();
        JsonNode responseArr = result == null ? null : result.path("response");
        boolean isMultiBatch = responseArr != null && responseArr.isArray() && responseArr.size() > 1;

        if (isMultiBatch) {
            // 多批次：每个批次作为一个分片入库
            List<OcrFileSplit> splits = buildSplitSkeletons(main, responseArr.size());
            // 回填每批图片 base64（由 MultimodalService 在 batchImageBase64s 中对齐批次数）
            JsonNode batchImages = result.path("batchImageBase64s");
            for (int i = 0; i < responseArr.size() && i < splits.size(); i++) {
                String imageBase64 = "";
                if (batchImages.isArray() && i < batchImages.size()) {
                    // element 形如 ["base641","base642"]，入库时直接存为 JSON 字符串便于回溯
                    imageBase64 = batchImages.get(i).toString();
                }
                splits.get(i).setImageBase64(imageBase64);
            }
            ocrFileDataService.saveSplits(main.getId(), splits);

            List<String> batchTexts = extractBatchTexts(result.path("content").asText(""), responseArr.size());

            // batchSuccesses：由 MultimodalService.callImg2TextInBatches 按批写入，
            // 下标与 response / batchImageBase64s 数组严格对齐。
            // 优先使用逐批标志；无该字段时（旧版兼容）回退到全局 success。
            JsonNode batchSuccesses = result.path("batchSuccesses");

            for (int i = 0; i < responseArr.size(); i++) {
                OcrFileSplit split = splits.get(i);

                // ── 关键修复：按批次独立判断成功，不再依赖全局 success ──
                // 旧逻辑：boolean batchOk = result.path("success").asBoolean(false);
                // 问题：任意批次失败 → allSuccess=false → 首批(i=0)立即报"批次0失败"，
                //       其余批次（含成功批次）完全不处理。
                boolean batchOk;
                if (batchSuccesses.isArray() && i < batchSuccesses.size()) {
                    batchOk = batchSuccesses.get(i).asBoolean(false);
                } else {
                    // 兼容无 batchSuccesses 字段的旧响应
                    batchOk = result.path("success").asBoolean(false);
                }

                String batchText = i < batchTexts.size() ? batchTexts.get(i) : "";

                if (batchOk) {
                    ocrFileDataService.saveSplitResult(split.getId(), main.getId(), prompt, batchText);
                } else {
                    String err = "正言多模态批次 " + i + " 识别失败";
                    ocrFileDataService.markSplitFailed(split.getId(), main.getId(), err);
                    // 不 return：继续处理其余批次，保留已成功批次的文本
                }
            }
        } else {
            // 单次识别
            String content = result == null ? "" : result.path("content").asText("");
            // 单批 base64：从 batchImageBase64s[0] 提取
            String imageBase64 = "";
            if (result != null) {
                JsonNode batchImages = result.path("batchImageBase64s");
                if (batchImages.isArray() && batchImages.size() > 0) {
                    imageBase64 = batchImages.get(0).toString();
                }
            }
            ocrFileDataService.saveDirectResult(main, prompt, content, imageBase64);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 构建辅助
    // ─────────────────────────────────────────────────────────

    private OcrFileMain buildMainEntity(OcrRecognitionRequest req) {
        OcrFileMain main = new OcrFileMain();
        main.setBusinessNo(req.getBusinessNo());
        main.setSource(req.getSource());
        main.setFileName(req.getFileName());
        main.setFileType(req.getFileType());
        main.setFileSize(req.getFileSize());
        main.setFilePath(req.getFilePath());
        main.setAppCode(req.getAppCode());
        main.setExtraInfo(req.getExtraInfo());
        main.setPrompt(req.getPrompt());
        return main;
    }

    /**
     * 为多页/多批场景预建分片骨架（仅含序号，其余字段由识别结果回填）。
     * 单图时 filePath 与主文件相同；多页时各页 filePath 通常一样（整文件拆分场景），
     * 若各页有独立 MinIO 路径，调用方可在 saveSplits 后自行更新。
     */
    private List<OcrFileSplit> buildSplitSkeletons(OcrFileMain main, int count) {
        List<OcrFileSplit> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            OcrFileSplit split = new OcrFileSplit();
            split.setSplitIndex(i);
            split.setPageNo(i + 1);
            split.setFilePath(main.getFilePath());
            split.setFileType(main.getFileType());
            split.setFileSize(main.getFileSize());
            list.add(split);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────
    // 文本提取工具
    // ─────────────────────────────────────────────────────────

    /**
     * 从大智部识别响应中提取文本。
     *
     * 说明：
     * - MultimodalService.dazhiOcrGeneral（attachments 拆分）返回的 pages[i] 通常是 DazhiOcrTool 的“统一响应”（包含 response.resultMsg）。
     * - 单页未拆分时，result 可能没有顶层 content，因此需要从 response.resultMsg.picList 中提取。
     */
    private String extractDazhiText(JsonNode pageNode) {
        if (pageNode == null) return "";

        // 1) 优先取顶层 content 字段（dazhiOcrGeneral 已聚合时会有）
        String content = pageNode.path("content").asText("");
        if (!content.isBlank()) return content;

        // 2) 再取 response.data.text（部分上游可能直接给文本）
        JsonNode response = pageNode.path("response");
        String dataText = response.path("data").path("text").asText("");
        if (!dataText.isBlank()) return dataText;

        // 3) 解析 resultMsg.picList.picContent.contents
        JsonNode picList = response.path("resultMsg").path("picList");
        if (!picList.isArray()) {
            // 兼容：有些结构可能 resultMsg 在顶层（不太常见，但兜底一下）
            picList = pageNode.path("resultMsg").path("picList");
        }

        if (picList.isArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonNode pic : picList) {
                JsonNode contents = pic.path("picContent").path("contents");
                if (contents.isArray()) {
                    for (JsonNode line : contents) {
                        // contents 的元素有可能是纯字符串，也可能是对象（这里统一转成文本）
                        String t = line.isTextual() ? line.asText("") : line.path("text").asText("");
                        t = t == null ? "" : t.trim();
                        if (!t.isBlank()) lines.add(t);
                    }
                } else {
                    // 兜底：如果 contents 不是数组，尝试直接读成文本
                    String t = contents.asText("");
                    t = t == null ? "" : t.trim();
                    if (!t.isBlank()) lines.add(t);
                }
            }
            if (!lines.isEmpty()) return String.join("\n", lines);
        }

        return "";
    }

    /**
     * 将正言多模态的聚合 content 按批次页标拆分，还原各批次文本。
     * content 格式：[第1-2页]\n内容\n\n[第3-4页]\n内容
     */
    private List<String> extractBatchTexts(String aggregatedContent, int batchCount) {
        List<String> result = new ArrayList<>();
        if (aggregatedContent == null || aggregatedContent.isBlank()) {
            for (int i = 0; i < batchCount; i++) result.add("");
            return result;
        }
        // 按 [第X-Y页] 分隔符拆分
        String[] parts = aggregatedContent.split("\\[第\\d+-\\d+页\\]");
        for (int i = 0; i < batchCount; i++) {
            // parts[0] 是分隔符前的空串，实际内容从 parts[1] 开始
            int partIdx = i + 1;
            result.add(partIdx < parts.length ? parts[partIdx].trim() : "");
        }
        return result;
    }

    private String truncate(String msg, int maxLen) {
        if (msg == null) return "";
        return msg.length() <= maxLen ? msg : msg.substring(0, maxLen);
    }
}
