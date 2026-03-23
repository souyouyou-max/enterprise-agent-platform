package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文件相似度对比结果实体（对应 file_similarity_result 表）
 *
 * <p>存储多附件对比的两类相似度：
 * <ul>
 *   <li><b>文字相似度</b>：通过读取 {@code ocr_file_split.ocr_result}（分段OCR识别结果）
 *       拼接后调用 Python bid-analysis-service 进行 TF-IDF + difflib 对比得出。</li>
 *   <li><b>文件整体相似度</b>：对完整文件字节做 SHA-256 精确匹配，以及
 *       PDF/图片的感知哈希（dHash）均值相似度。</li>
 * </ul>
 */
@Data
@TableName("file_similarity_result")
public class FileSimilarityResult {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 同一批对比的唯一业务流水号 */
    @TableField("business_no")
    private String businessNo;

    /** 关联 ocr_file_main.id（文件A） */
    @TableField("file_a_main_id")
    private Long fileAMainId;

    /** 关联 ocr_file_main.id（文件B） */
    @TableField("file_b_main_id")
    private Long fileBMainId;

    /** 文件A名称 */
    @TableField("file_a_name")
    private String fileAName;

    /** 文件B名称 */
    @TableField("file_b_name")
    private String fileBName;

    /** 文件A SHA-256 摘要 */
    @TableField("file_a_sha256")
    private String fileASha256;

    /** 文件B SHA-256 摘要 */
    @TableField("file_b_sha256")
    private String fileBSha256;

    // ── 文字相似度（分段OCR结果对比）────────────────────────────

    /** TF-IDF 余弦相似度（0~1） */
    @TableField("text_tfidf_cosine")
    private BigDecimal textTfidfCosine;

    /** difflib SequenceMatcher ratio（0~1） */
    @TableField("text_difflib_ratio")
    private BigDecimal textDifflibRatio;

    /** 最长公共连续块字符数 */
    @TableField("text_longest_common")
    private Integer textLongestCommon;

    /** 匹配片段数（≥50字符） */
    @TableField("text_segments_50")
    private Integer textSegments50;

    /** 长公共块数（≥500字符） */
    @TableField("text_blocks_500")
    private Integer textBlocks500;

    /** 文件A有效文字长度 */
    @TableField("text_len_a")
    private Integer textLenA;

    /** 文件B有效文字长度 */
    @TableField("text_len_b")
    private Integer textLenB;

    // ── 文件整体相似度（整文件直接对比）─────────────────────────

    /** 是否完全相同（SHA-256精确匹配）：true=完全相同 */
    @TableField("file_exact_match")
    private Boolean fileExactMatch;

    /** 视觉感知哈希平均相似度（0~1），PDF/图片可用，其他格式为 null */
    @TableField("file_visual_sim")
    private BigDecimal fileVisualSim;

    // ── 汇总风险 ────────────────────────────────────────────────

    /** 综合风险等级：high / medium / low / unknown */
    @TableField("risk_level")
    private String riskLevel;

    /** 风险描述文字 */
    @TableField("risk_label")
    private String riskLabel;

    // ── 元数据 ──────────────────────────────────────────────────

    /** 应用/来源标识 */
    @TableField("app_code")
    private String appCode;

    /** 扩展 JSON（完整 Python 响应 comparisons 字段） */
    @TableField("extra_detail")
    private String extraDetail;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
