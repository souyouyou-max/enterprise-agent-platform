-- 已有库增量：ocr_file_analysis 增加 structured_extra（Oracle / OceanBase Oracle 兼容）
-- 在对应 SCHEMA 下以 DBA 或有 ALTER 权限账号执行一次即可。

-- 方式一：直接执行（若列已存在会报错 ORA-01430，可忽略或改用方式二）
-- ALTER TABLE ocr_file_analysis ADD structured_extra CLOB;

-- 方式二：仅当列不存在时添加（可重复执行）
DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM user_tab_columns
   WHERE table_name = 'OCR_FILE_ANALYSIS'
     AND column_name = 'STRUCTURED_EXTRA';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE ocr_file_analysis ADD structured_extra CLOB';
  END IF;
END;
/

COMMENT ON COLUMN ocr_file_analysis.structured_extra IS '证件/身份证等扩展字段 JSON';
