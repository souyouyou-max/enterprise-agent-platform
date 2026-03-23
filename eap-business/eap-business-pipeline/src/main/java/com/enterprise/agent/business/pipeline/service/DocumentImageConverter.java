package com.enterprise.agent.business.pipeline.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class DocumentImageConverter {

    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "webp", "bmp", "gif");
    private static final Set<String> PDF_EXTS = Set.of("pdf");
    private static final Set<String> OFFICE_EXTS = Set.of("doc", "docx", "ppt", "pptx");

    @Value("${eap.tools.zhengyan.platform.convert.max-pages:20}")
    private int maxPages;

    @Value("${eap.tools.zhengyan.platform.convert.dpi:160}")
    private int dpi;

    @Value("${eap.tools.zhengyan.platform.convert.mode:pure-java}")
    private String mode;

    public List<String> toImageDataUrls(String fileName, String mimeType, String base64) throws Exception {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("附件base64不能为空");
        }
        String ext = detectExt(fileName, mimeType);
        byte[] bytes = Base64.getDecoder().decode(base64);

        if (IMAGE_EXTS.contains(ext)) {
            return List.of(toDataUrl(bytes, imageMimeFromExt(ext)));
        }
        if (PDF_EXTS.contains(ext)) {
            return renderPdfToJpegDataUrls(bytes);
        }
        if (OFFICE_EXTS.contains(ext)) {
            return renderOfficePureJava(bytes, ext);
        }
        throw new IllegalArgumentException("暂不支持的附件类型: " + ext);
    }

    private List<String> renderPdfToJpegDataUrls(byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            int renderPages = Math.min(pageCount, maxPages);
            PDFRenderer renderer = new PDFRenderer(document);
            List<String> dataUrls = new ArrayList<>();
            for (int i = 0; i < renderPages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", out);
                dataUrls.add(toDataUrl(out.toByteArray(), "image/jpeg"));
            }
            if (pageCount > maxPages) {
                log.warn("[DocumentImageConverter] PDF页数过多，仅处理前{}页，总页数={}", maxPages, pageCount);
            }
            return dataUrls;
        }
    }

    private List<String> renderOfficePureJava(byte[] source, String ext) throws Exception {
        if (!"pure-java".equalsIgnoreCase(mode)) {
            log.info("[DocumentImageConverter] 当前模式={}, 但已强制使用 pure-java（不依赖 soffice）", mode);
        }
        return switch (ext) {
            case "pptx" -> renderPptxToJpegDataUrls(source);
            case "ppt" -> renderPptToJpegDataUrls(source);
            case "docx" -> renderDocxToJpegDataUrls(source);
            case "doc" -> renderDocToJpegDataUrls(source);
            default -> throw new IllegalArgumentException("暂不支持的office类型: " + ext);
        };
    }

    private List<String> renderPptxToJpegDataUrls(byte[] source) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(source))) {
            int count = Math.min(maxPages, ppt.getSlides().size());
            Dimension pageSize = ppt.getPageSize();
            double scale = dpi / 72.0;
            int width = (int) (pageSize.width * scale);
            int height = (int) (pageSize.height * scale);
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
                g.scale(scale, scale);
                ppt.getSlides().get(i).draw(g);
                g.dispose();
                urls.add(toDataUrl(toJpegBytes(image), "image/jpeg"));
            }
            return urls;
        }
    }

    private List<String> renderPptToJpegDataUrls(byte[] source) throws Exception {
        try (HSLFSlideShow ppt = new HSLFSlideShow(new ByteArrayInputStream(source))) {
            int count = Math.min(maxPages, ppt.getSlides().size());
            Dimension pageSize = ppt.getPageSize();
            double scale = dpi / 72.0;
            int width = (int) (pageSize.width * scale);
            int height = (int) (pageSize.height * scale);
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
                g.scale(scale, scale);
                ppt.getSlides().get(i).draw(g);
                g.dispose();
                urls.add(toDataUrl(toJpegBytes(image), "image/jpeg"));
            }
            return urls;
        }
    }

    private List<String> renderDocxToJpegDataUrls(byte[] source) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(source))) {
            List<String> paragraphs = new ArrayList<>();
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    paragraphs.add(text.trim());
                }
            }
            return renderTextParagraphPages(paragraphs);
        }
    }

    private List<String> renderDocToJpegDataUrls(byte[] source) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(source));
             WordExtractor extractor = new WordExtractor(doc)) {
            List<String> paragraphs = new ArrayList<>();
            for (String text : extractor.getParagraphText()) {
                if (text != null && !text.isBlank()) {
                    paragraphs.add(text.trim());
                }
            }
            return renderTextParagraphPages(paragraphs);
        }
    }

    // 纯Java文档渲染：按字符数近似分页（不依赖soffice，非高保真）
    private List<String> renderTextParagraphPages(List<String> paragraphs) throws Exception {
        final int width = (int) (210 * dpi / 25.4);
        final int height = (int) (297 * dpi / 25.4);
        final int maxCharsPerPage = 1800;
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String p : paragraphs) {
            if (current.length() + p.length() + 1 > maxCharsPerPage) {
                if (!current.isEmpty()) {
                    pages.add(current.toString());
                    current = new StringBuilder();
                }
            }
            current.append(p).append("\n");
        }
        if (!current.isEmpty()) {
            pages.add(current.toString());
        }
        if (pages.isEmpty()) {
            pages.add("文档无可提取文本内容");
        }
        int count = Math.min(maxPages, pages.size());
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(12, dpi / 12)));
            drawWrappedText(g, pages.get(i), 40, 60, width - 80, Math.max(22, dpi / 10));
            g.dispose();
            urls.add(toDataUrl(toJpegBytes(image), "image/jpeg"));
        }
        return urls;
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics metrics = g.getFontMetrics();
        String[] paragraphs = text.split("\n");
        int lineY = y;
        for (String p : paragraphs) {
            if (p.isBlank()) {
                lineY += lineHeight;
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (char c : p.toCharArray()) {
                line.append(c);
                if (metrics.stringWidth(line.toString()) >= maxWidth) {
                    g.drawString(line.toString(), x, lineY);
                    lineY += lineHeight;
                    line = new StringBuilder();
                    if (lineY > g.getClipBounds().height - lineHeight) {
                        return;
                    }
                }
            }
            if (!line.isEmpty()) {
                g.drawString(line.toString(), x, lineY);
                lineY += lineHeight;
            }
            if (lineY > g.getClipBounds().height - lineHeight) {
                return;
            }
        }
    }

    private byte[] toJpegBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private String detectExt(String fileName, String mimeType) {
        String extFromName = "";
        if (fileName != null && fileName.contains(".")) {
            extFromName = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        if (!extFromName.isBlank()) {
            return extFromName;
        }
        if (mimeType == null) {
            return "";
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> "pdf";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.ms-powerpoint" -> "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "";
        };
    }

    private String imageMimeFromExt(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
    }

    private String toDataUrl(byte[] bytes, String mime) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}

