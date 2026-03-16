package com.enterprise.agent.engine.rule.parser;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 投标文件解析器：支持 PDF / DOCX / DOC / XLSX / XLS 格式
 */
@Slf4j
@Component
public class BidDocumentParser {

    @Autowired(required = false)
    private MinioClient minioClient;

    /**
     * 从字节数组解析文本内容
     */
    public String parseFromBytes(byte[] data, String filename) {
        String ext = extractExtension(filename).toLowerCase();
        try {
            return switch (ext) {
                case "pdf"  -> parsePdf(data);
                case "docx" -> parseDocx(data);
                case "doc"  -> parseDoc(data);
                case "xlsx" -> parseXlsx(data);
                case "xls"  -> parseXls(data);
                default     -> new String(data, StandardCharsets.UTF_8);
            };
        } catch (Exception e) {
            log.warn("[BidDocumentParser] 解析文件失败：filename={}，原因：{}", filename, e.getMessage());
            return null;
        }
    }

    /**
     * 从 MinIO 读取并解析文本内容；MinIO 不可用时返回 null
     */
    public String parseFromMinio(String bucket, String objectPath) {
        if (minioClient == null) {
            log.warn("[BidDocumentParser] MinIO 未启用，无法解析：{}", objectPath);
            return null;
        }
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectPath).build())) {
            byte[] data = is.readAllBytes();
            String filename = objectPath.contains("/")
                    ? objectPath.substring(objectPath.lastIndexOf('/') + 1)
                    : objectPath;
            return parseFromBytes(data, filename);
        } catch (Exception e) {
            log.warn("[BidDocumentParser] MinIO 读取失败：{}，原因：{}", objectPath, e.getMessage());
            return null;
        }
    }

    // ---- 各格式解析 ----

    private String parsePdf(byte[] data) throws Exception {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(data))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String parseDocx(byte[] data) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String parseDoc(byte[] data) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(data));
             WordExtractor extractor = new WordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String parseXlsx(byte[] data) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            return extractWorkbookText(wb);
        }
    }

    private String parseXls(byte[] data) throws Exception {
        try (Workbook wb = new HSSFWorkbook(new ByteArrayInputStream(data))) {
            return extractWorkbookText(wb);
        }
    }

    private String extractWorkbookText(Workbook wb) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet sheet = wb.getSheetAt(i);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    sb.append(cell.toString()).append("\t");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String extractExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx + 1) : "";
    }
}
