package com.sinosig.aip.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinosig.aip.data.entity.FileSimilarityResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文件相似度对比结果 Mapper
 */
@Mapper
public interface FileSimilarityResultMapper extends BaseMapper<FileSimilarityResult> {

    @Select("SELECT * FROM file_similarity_result WHERE business_no = #{businessNo} ORDER BY created_at DESC")
    List<FileSimilarityResult> findByBusinessNo(@Param("businessNo") String businessNo);

    @Select("SELECT * FROM file_similarity_result WHERE file_a_main_id = #{mainId} OR file_b_main_id = #{mainId} ORDER BY created_at DESC")
    List<FileSimilarityResult> findByMainFileId(@Param("mainId") Long mainId);
}
