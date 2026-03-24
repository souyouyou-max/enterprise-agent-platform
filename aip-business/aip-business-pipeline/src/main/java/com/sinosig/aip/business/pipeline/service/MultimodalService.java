package com.sinosig.aip.business.pipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 多模态处理服务接口。
 * 定义图文理解（img2text）、通用 OCR、智能路由、专业问答等多模态能力的统一契约。
 */
public interface MultimodalService {

    /**
     * 正言图文理解（img2text）。
     * 若请求体含 attachments，按批次分页调用并聚合；否则直接透传。
     *
     * @return 统一视图：{action, success, httpStatus, content, response}
     */
    ObjectNode img2Text(ObjectNode request) throws Exception;

    /**
     * 智能 OCR 路由：由 LLM 根据用户意图选择 img2text 或 dazhi-ocr，返回统一格式。
     *
     * @return 统一视图：{engine, success, content, ...}
     */
    ObjectNode autoOcr(ObjectNode request) throws Exception;

    /**
     * 大智部通用 OCR（含附件分页支持）。
     *
     * @return 聚合结果：{success, pageCount, content, pages}
     */
    JsonNode dazhiOcrGeneral(ObjectNode request) throws Exception;
}
