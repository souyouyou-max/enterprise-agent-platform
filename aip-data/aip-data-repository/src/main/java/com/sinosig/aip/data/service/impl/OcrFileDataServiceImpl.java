package com.sinosig.aip.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sinosig.aip.data.entity.OcrFileMain;
import com.sinosig.aip.data.entity.OcrFileSplit;
import com.sinosig.aip.data.mapper.OcrFileMainMapper;
import com.sinosig.aip.data.mapper.OcrFileSplitMapper;
import com.sinosig.aip.data.service.OcrFileDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OCR文件数据服务实现
 * <p>
 * 所有 DB 操作均通过 MyBatis-Plus LambdaWrapper，不使用手写 SQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrFileDataServiceImpl implements OcrFileDataService {

    private final OcrFileMainMapper mainMapper;
    private final OcrFileSplitMapper splitMapper;

    // ── 主文件：生命周期 ────────────────────────────────────────

    @Override
    @Transactional
    public OcrFileMain createMain(OcrFileMain main) {
        main.setOcrStatus(STATUS_PENDING);
        mainMapper.insert(main);
        log.info("[OcrFile] 主文件记录创建, id={}, businessNo={}, source={}",
                main.getId(), main.getBusinessNo(), main.getSource());
        return main;
    }

    @Override
    public void markMainProcessing(Long id) {
        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, id)
                .set(OcrFileMain::getOcrStatus, STATUS_PROCESSING));
        log.info("[OcrFile] 主文件开始识别, id={}", id);
    }

    @Override
    @Transactional
    public void saveDirectResult(OcrFileMain mainFile, String prompt, String ocrResult, String splitImageBase64) {
        Long id = mainFile.getId();

        // 写入 split 表（单张图片对应一条分片记录，split_index=0，page_no=1）
        OcrFileSplit singleSplit = new OcrFileSplit();
        singleSplit.setMainId(id);
        singleSplit.setSplitIndex(0);
        singleSplit.setPageNo(1);
        singleSplit.setFilePath(mainFile.getFilePath());
        singleSplit.setFileType(mainFile.getFileType());
        singleSplit.setFileSize(mainFile.getFileSize());
        singleSplit.setOcrStatus(STATUS_SUCCESS);
        singleSplit.setPrompt(prompt);
        singleSplit.setOcrResult(ocrResult);
        singleSplit.setImageBase64(splitImageBase64);
        splitMapper.insert(singleSplit);

        // 更新主表：prompt、ocrResult、total_pages=1、status→SUCCESS
        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, id)
                .set(OcrFileMain::getOcrStatus, STATUS_SUCCESS)
                .set(OcrFileMain::getTotalPages, 1)
                .set(OcrFileMain::getPrompt, prompt)
                .set(OcrFileMain::getOcrResult, ocrResult));
        log.info("[OcrFile] 单图识别完成，主表+分片表均已写入, id={}", id);
    }

    @Override
    public void markMainFailed(Long id, String errorMessage) {
        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, id)
                .set(OcrFileMain::getOcrStatus, STATUS_FAILED)
                .set(OcrFileMain::getErrorMessage, errorMessage));
        log.error("[OcrFile] 主文件标记失败, id={}, error={}", id, errorMessage);
    }

    // ── 主文件：查询 ────────────────────────────────────────────

    @Override
    public OcrFileMain findMainById(Long id) {
        return mainMapper.selectById(id);
    }

    @Override
    public OcrFileMain findMainByBusinessNo(String businessNo) {
        return mainMapper.selectOne(new LambdaQueryWrapper<OcrFileMain>()
                .eq(OcrFileMain::getBusinessNo, businessNo));
    }

    @Override
    public List<OcrFileMain> findMainByStatus(String status) {
        return mainMapper.selectList(new LambdaQueryWrapper<OcrFileMain>()
                .eq(OcrFileMain::getOcrStatus, status)
                .orderByAsc(OcrFileMain::getCreatedAt));
    }

    @Override
    public List<OcrFileMain> findMainBySourceAndStatus(String source, String status) {
        return mainMapper.selectList(new LambdaQueryWrapper<OcrFileMain>()
                .eq(OcrFileMain::getSource, source)
                .eq(OcrFileMain::getOcrStatus, status)
                .orderByAsc(OcrFileMain::getCreatedAt));
    }

    @Override
    public List<OcrFileMain> findMainList(String source, String status, Integer limit) {
        int l = limit == null ? 50 : limit;
        if (l <= 0) l = 50;

        LambdaQueryWrapper<OcrFileMain> wrapper = new LambdaQueryWrapper<OcrFileMain>()
                .orderByDesc(OcrFileMain::getCreatedAt)
                .last("FETCH FIRST " + l + " ROWS ONLY");

        if (source != null && !source.isBlank()) {
            wrapper.eq(OcrFileMain::getSource, source);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(OcrFileMain::getOcrStatus, status);
        }

        return mainMapper.selectList(wrapper);
    }

    // ── 分片：生命周期（多页/拆分路径）─────────────────────────

    @Override
    @Transactional
    public void saveSplits(Long mainId, List<OcrFileSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            log.warn("[OcrFile] 分片列表为空，跳过保存, mainId={}", mainId);
            return;
        }
        for (OcrFileSplit split : splits) {
            split.setMainId(mainId);
            split.setOcrStatus(STATUS_PENDING);
            splitMapper.insert(split);
        }
        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, mainId)
                .set(OcrFileMain::getTotalPages, splits.size())
                .set(OcrFileMain::getOcrStatus, STATUS_PROCESSING));
        log.info("[OcrFile] 分片批量保存完成, mainId={}, count={}", mainId, splits.size());
    }

    @Override
    @Transactional
    public void saveSplitResult(Long splitId, Long mainId, String prompt, String ocrResult) {
        splitMapper.update(new LambdaUpdateWrapper<OcrFileSplit>()
                .eq(OcrFileSplit::getId, splitId)
                .set(OcrFileSplit::getOcrStatus, STATUS_SUCCESS)
                .set(OcrFileSplit::getPrompt, prompt)
                .set(OcrFileSplit::getOcrResult, ocrResult));
        log.info("[OcrFile] 分片识别结果已保存, splitId={}, mainId={}", splitId, mainId);
        checkAndAggregate(mainId);
    }

    @Override
    @Transactional
    public void saveSplitLlmResult(Long splitId, Long mainId, String llmResult) {
        splitMapper.update(new LambdaUpdateWrapper<OcrFileSplit>()
                .eq(OcrFileSplit::getId, splitId)
                .set(OcrFileSplit::getLlmResult, llmResult));
        log.info("[OcrFile] 分片大模型结果已保存, splitId={}, mainId={}", splitId, mainId);
    }

    @Override
    @Transactional
    public void markSplitFailed(Long splitId, Long mainId, String errorMessage) {
        splitMapper.update(new LambdaUpdateWrapper<OcrFileSplit>()
                .eq(OcrFileSplit::getId, splitId)
                .set(OcrFileSplit::getOcrStatus, STATUS_FAILED)
                .set(OcrFileSplit::getErrorMessage, errorMessage));
        log.warn("[OcrFile] 分片识别失败, splitId={}, mainId={}, error={}", splitId, mainId, errorMessage);
        markMainFailed(mainId, String.format("分片(id=%d)识别失败: %s", splitId, errorMessage));
    }

    // ── 分片：查询 ──────────────────────────────────────────────

    @Override
    public List<OcrFileSplit> findSplitsByMainId(Long mainId) {
        return splitMapper.selectList(new LambdaQueryWrapper<OcrFileSplit>()
                .eq(OcrFileSplit::getMainId, mainId)
                .orderByAsc(OcrFileSplit::getSplitIndex));
    }

    @Override
    public long countSplitsByStatus(Long mainId, String status) {
        return splitMapper.selectCount(new LambdaQueryWrapper<OcrFileSplit>()
                .eq(OcrFileSplit::getMainId, mainId)
                .eq(OcrFileSplit::getOcrStatus, status));
    }

    // ── 重试 ────────────────────────────────────────────────────

    @Override
    @Transactional
    public void resetForReprocess(Long mainId) {
        splitMapper.delete(new LambdaQueryWrapper<OcrFileSplit>()
                .eq(OcrFileSplit::getMainId, mainId));
        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, mainId)
                .set(OcrFileMain::getOcrStatus, STATUS_PENDING)
                .set(OcrFileMain::getAnalysisStatus, STATUS_PENDING)
                .set(OcrFileMain::getErrorMessage, null)
                .set(OcrFileMain::getOcrResult, null)
                .set(OcrFileMain::getTotalPages, 0));
        log.info("[OcrFile] 主文件已重置以便重新处理, mainId={}", mainId);
    }

    // ── 批次维度查询 ─────────────────────────────────────────────

    @Override
    public List<OcrFileMain> findMainByBatchNo(String batchNo) {
        return mainMapper.selectList(new LambdaQueryWrapper<OcrFileMain>()
                .eq(OcrFileMain::getBatchNo, batchNo)
                .orderByAsc(OcrFileMain::getCreatedAt));
    }

    @Override
    public long countMainByBatchAndOcrStatus(String batchNo, String ocrStatus) {
        return mainMapper.selectCount(new LambdaQueryWrapper<OcrFileMain>()
                .eq(OcrFileMain::getBatchNo, batchNo)
                .eq(OcrFileMain::getOcrStatus, ocrStatus));
    }

    @Override
    public long countMainByBatchAndAnalysisStatus(String batchNo, String analysisStatus) {
        return mainMapper.selectCount(new LambdaQueryWrapper<OcrFileMain>()
                .eq(OcrFileMain::getBatchNo, batchNo)
                .eq(OcrFileMain::getAnalysisStatus, analysisStatus));
    }

    // ── 多模态分析状态更新 ────────────────────────────────────────

    @Override
    public void markAnalysisProcessing(Long mainId) {
        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, mainId)
                .set(OcrFileMain::getAnalysisStatus, "PROCESSING"));
        log.info("[OcrFile] 多模态分析开始, mainId={}", mainId);
    }

    @Override
    public void markAnalysisSuccess(Long mainId) {
        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, mainId)
                .set(OcrFileMain::getAnalysisStatus, STATUS_SUCCESS));
        log.info("[OcrFile] 多模态分析完成, mainId={}", mainId);
    }

    @Override
    public void markAnalysisFailed(Long mainId, String errorMessage) {
        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, mainId)
                .set(OcrFileMain::getAnalysisStatus, STATUS_FAILED)
                .set(OcrFileMain::getErrorMessage, truncate(errorMessage, 900)));
        log.warn("[OcrFile] 多模态分析失败, mainId={}, error={}", mainId, errorMessage);
    }

    @Override
    public void markAnalysisSkipped(Long mainId) {
        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, mainId)
                .set(OcrFileMain::getAnalysisStatus, "SKIPPED"));
        log.info("[OcrFile] 多模态分析跳过（无图片内容）, mainId={}", mainId);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ── 内部：分片聚合 ───────────────────────────────────────────

    /**
     * 检查所有分片是否全部 SUCCESS，若是则聚合文本写入主表（status→SUCCESS）。
     * 由 saveSplitResult 自动触发，不对外暴露。
     */
    private void checkAndAggregate(Long mainId) {
        long total   = splitMapper.selectCount(new LambdaQueryWrapper<OcrFileSplit>()
                .eq(OcrFileSplit::getMainId, mainId));
        long success = countSplitsByStatus(mainId, STATUS_SUCCESS);
        long failed  = countSplitsByStatus(mainId, STATUS_FAILED);

        log.debug("[OcrFile] 聚合检查, mainId={}, total={}, success={}, failed={}", mainId, total, success, failed);

        if (failed > 0) {
            // markSplitFailed 已处理主表状态，无需重复操作
            return;
        }
        if (success < total) {
            // 仍有分片待处理，继续等待
            return;
        }

        List<OcrFileSplit> splits = findSplitsByMainId(mainId);
        String aggregated = splits.stream()
                .sorted(Comparator.comparingInt(OcrFileSplit::getSplitIndex))
                .map(s -> s.getOcrResult() != null ? s.getOcrResult() : "")
                .collect(Collectors.joining("\n"));

        mainMapper.update(new LambdaUpdateWrapper<OcrFileMain>()
                .eq(OcrFileMain::getId, mainId)
                .set(OcrFileMain::getOcrStatus, STATUS_SUCCESS)
                .set(OcrFileMain::getTotalPages, (int) total)
                .set(OcrFileMain::getOcrResult, aggregated));
        log.info("[OcrFile] 所有分片识别完成，结果已聚合写入主文件, mainId={}, totalPages={}", mainId, total);
    }
}
