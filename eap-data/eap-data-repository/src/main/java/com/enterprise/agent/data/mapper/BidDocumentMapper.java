package com.enterprise.agent.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.agent.data.entity.ProcurementBid;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface BidDocumentMapper extends BaseMapper<ProcurementBid> {

    /** 查询项目下已上传MinIO的投标文件 */
    @Select("SELECT * FROM procurement_bid WHERE bid_project_id = #{projectId} AND bid_document_minio_pth IS NOT NULL")
    List<ProcurementBid> findBidsByProject(@Param("projectId") String projectId);

    /** 查询机构下有MinIO文件的项目ID（通过procurement_project关联获取org_code） */
    @Select("SELECT DISTINCT pb.bid_project_id " +
            "FROM procurement_bid pb " +
            "JOIN procurement_project pp ON pb.bid_project_id = pp.project_code " +
            "WHERE pp.org_code = #{orgCode} AND pb.bid_document_minio_pth IS NOT NULL")
    List<String> findProjectsWithDocuments(@Param("orgCode") String orgCode);

    /** 查询机构下有文档文本（直接文本或MinIO）的项目ID */
    @Select("SELECT DISTINCT pb.bid_project_id " +
            "FROM procurement_bid pb " +
            "JOIN procurement_project pp ON pb.bid_project_id = pp.project_code " +
            "WHERE pp.org_code = #{orgCode} " +
            "  AND (pb.bid_document_text IS NOT NULL OR pb.bid_document_minio_pth IS NOT NULL)")
    List<String> findProjectsWithDocumentText(@Param("orgCode") String orgCode);

    /** 查询项目下有文档文本的投标记录（用于相似度分析） */
    @Select("SELECT * FROM procurement_bid WHERE bid_project_id = #{projectId} " +
            "AND (bid_document_text IS NOT NULL OR bid_document_minio_pth IS NOT NULL)")
    List<ProcurementBid> findBidsWithTextByProject(@Param("projectId") String projectId);

    @Update("UPDATE procurement_bid SET bid_document_minio_pth = #{path}, bid_document_text = #{text} WHERE id = #{id}")
    void updateDocumentInfo(@Param("id") Long id, @Param("path") String path, @Param("text") String text);

    @Select("SELECT * FROM procurement_bid WHERE bid_project_id = #{projectId}")
    List<ProcurementBid> findAllBidsByProject(@Param("projectId") String projectId);
}
