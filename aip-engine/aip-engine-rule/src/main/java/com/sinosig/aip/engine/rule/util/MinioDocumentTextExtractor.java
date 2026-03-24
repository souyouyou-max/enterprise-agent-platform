package com.sinosig.aip.engine.rule.util;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
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
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 MinIO 读取文件并解析为纯文本（PDF/DOCX/DOC/XLSX/XLS/UTF-8文本）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioDocumentTextExtractor {

    @Autowired(required = false)
    private MinioClient minioClient;

    public String extract(String bucket, String objectPath) {
        if (minioClient == null) {
            log.warn("[MinioDocumentTextExtractor] MinIO 未启用，无法读取：{}", objectPath);
            return null;
        }
        byte[] data = fetchObjectBytes(bucket, objectPath);
        if (data == null || data.length == 0) return null;

        String filename = objectPath.contains("/") ? objectPath.substring(objectPath.lastIndexOf('/') + 1) : objectPath;
        return parseFromBytes(data, filename);
    }

    /**
     * 读取对象原始 bytes（带 objectPath 兜底重试：原始 / URLDecode / 空格→%20）
     */
    public byte[] fetchObjectBytes(String bucket, String objectPath) {
        if (minioClient == null) {
            log.warn("[MinioDocumentTextExtractor] MinIO 未启用，无法读取：{}", objectPath);
            return null;
        }

        List<String> candidates = buildCandidateObjectPaths(objectPath);
        Exception last = null;
        for (String obj : candidates) {
            try (InputStream is = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(obj).build())) {
                return is.readAllBytes();
            } catch (Exception e) {
                last = e;
            }
        }
        log.warn("[MinioDocumentTextExtractor] MinIO读取/解析失败：{}，原因：{}",
                objectPath, last != null ? last.getMessage() : "unknown");
        return null;
    }

    private List<String> buildCandidateObjectPaths(String objectPath) {
        List<String> out = new ArrayList<>();
        if (objectPath == null) return out;
        out.add(objectPath);

        if (objectPath.contains("%")) {
            try {
                String decoded = URLDecoder.decode(objectPath, StandardCharsets.UTF_8);
                if (!decoded.equals(objectPath)) out.add(decoded);
            } catch (Exception ignored) {
                // ignore
            }
        }
        if (objectPath.contains(" ")) {
            String encodedSpace = objectPath.replace(" ", "%20");
            if (!encodedSpace.equals(objectPath)) out.add(encodedSpace);
        }
        return out.stream().distinct().toList();
    }

    private String parseFromBytes(byte[] data, String filename) {
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
            log.warn("[MinioDocumentTextExtractor] 解析失败：filename={}，原因：{}", filename, e.getMessage());
            return null;
        }
    }

    private String parsePdf(byte[] data) throws Exception {
        try (PDDocument doc = Loader.loadPDF(data)) {
            String text = new PDFTextStripper().getText(doc);
            log.info("[MinioDocumentTextExtractor] PDF解析成功，文本长度：{},内容:{}", text.length(), text);
            return text;
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
                    sb.append(cell.toString()).append('\t');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String extractExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx + 1) : "";
    }
}

