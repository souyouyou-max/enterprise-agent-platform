-- 为已有库增加分片级大模型结果列（可重复执行：列已存在则跳过）
-- Oracle / OceanBase 兼容

DECLARE
  c NUMBER;
BEGIN
  SELECT COUNT(*) INTO c FROM user_tab_columns WHERE table_name = 'OCR_FILE_SPLIT' AND column_name = 'LLM_RESULT';
  IF c = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE ocr_file_split ADD llm_result CLOB';
    EXECUTE IMMEDIATE q'[COMMENT ON COLUMN ocr_file_split.llm_result IS '大模型/多模态识别正文（与 ocr_result 分列）']';
  END IF;
END;
/
