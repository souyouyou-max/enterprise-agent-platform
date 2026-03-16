package com.enterprise.agent.data.ingestion.service;

import com.enterprise.agent.data.entity.ProcurementBid;
import com.enterprise.agent.data.ingestion.config.MinioConfig;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Set;

/**
 * 投标文件下载器：从招采系统下载投标文件并上传到 MinIO
 */
@Slf4j
@Service
public class BidDocumentDownloader {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "doc", "xlsx", "xls");

    @Autowired(required = false)
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    private final RestTemplate restTemplate;

    public BidDocumentDownloader(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 下载投标文件并存入 MinIO，返回 MinIO 对象路径；MinIO 不可用时返回 null。
     */
    public String downloadAndStore(ProcurementBid bid) {
        if (minioClient == null) {
            log.warn("[BidDocumentDownloader] MinIO 未启用，跳过文件下载：bidId={}", bid.getId());
            return null;
        }
        if (bid.getBidDocumentUrl() == null || bid.getBidDocumentUrl().isBlank()) {
            return null;
        }

        String url = bid.getBidDocumentUrl();
        String filename = extractFilename(url);
        String ext = extractExtension(filename).toLowerCase();
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            log.warn("[BidDocumentDownloader] 不支持的文件类型：{}，跳过", ext);
            return null;
        }

        String objectPath = buildObjectPath(bid, filename);

        // 已存在则跳过重复下载
        if (objectExists(objectPath)) {
            log.info("[BidDocumentDownloader] 文件已存在，跳过：{}", objectPath);
            return objectPath;
        }

        try {
            byte[] data = restTemplate.getForObject(url, byte[].class);
            if (data == null || data.length == 0) {
                log.warn("[BidDocumentDownloader] 下载内容为空：{}", url);
                return null;
            }

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .build());

            log.info("[BidDocumentDownloader] 文件上传成功：{}", objectPath);
            return objectPath;
        } catch (Exception e) {
            log.warn("[BidDocumentDownloader] 文件下载/上传失败：url={}，原因：{}", url, e.getMessage());
            return null;
        }
    }

    private boolean objectExists(String objectPath) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectPath)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildObjectPath(ProcurementBid bid, String filename) {
        String projectId = bid.getBidProjectId() != null ? bid.getBidProjectId() : "unknown";
        String supplierId = bid.getSupplierId() != null ? bid.getSupplierId() : "unknown";
        return projectId + "/" + supplierId + "/" + filename;
    }

    private String extractFilename(String url) {
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }

    private String extractExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx + 1) : "";
    }
}
