from __future__ import annotations

import io
import logging
import os
from concurrent.futures import ThreadPoolExecutor
from fastapi import UploadFile
from pypdf import PdfReader
from docx import Document

from app.services.doc_legacy import parse_doc_bytes

logger = logging.getLogger("bid-analysis.parse")


async def parse_upload_to_text(f: UploadFile) -> str:
    data = await f.read()
    name = (f.filename or "").lower()
    logger.info("parse start file=%s sizeBytes=%d", f.filename, len(data))
    if name.endswith(".pdf"):
        text = _parse_pdf_bytes(data)
    elif name.endswith(".docx"):
        text = _parse_docx_bytes(data)
    elif name.endswith(".doc"):
        t = parse_doc_bytes(data)
        text = t if t else _bytes_to_text(data)
    else:
        text = _bytes_to_text(data)
    text_norm_len = len("".join(text.split()))
    text_oneline = " ".join(text.split())
    logger.info(
        "parse done file=%s method=%s textLen=%d content=%s",
        f.filename,
        _detect_method(name),
        text_norm_len,
        (text_oneline[:500] + "...") if len(text_oneline) > 500 else text_oneline,
    )
    return text


def _detect_method(name: str) -> str:
    if name.endswith(".pdf"):
        return "pdf"
    if name.endswith(".docx"):
        return "docx"
    if name.endswith(".doc"):
        return "doc(legacy)"
    return "text"


def _parse_pdf_bytes(data: bytes) -> str:
    # 1) try embedded text (fast)
    reader = PdfReader(io.BytesIO(data))
    parts: list[str] = []
    for p in reader.pages:
        try:
            parts.append(p.extract_text() or "")
        except Exception:
            parts.append("")
    text = "\n".join(parts).strip()

    # 2) OCR fallback for scanned PDFs
    ocr_enabled = os.getenv("OCR_ENABLED", "false").lower() in ("1", "true", "yes", "y")
    min_len = int(os.getenv("OCR_MIN_TEXT_LEN", "800"))
    norm_len = len("".join(text.split()))
    logger.info("pdf embedded textLen=%d ocrEnabled=%s ocrMinLen=%d", norm_len, ocr_enabled, min_len)
    if ocr_enabled and norm_len < min_len:
        logger.info("pdf text too short, falling back to OCR")
        ocr_text = _ocr_pdf_bytes(data)
        if ocr_text:
            logger.info("pdf OCR result textLen=%d", len("".join(ocr_text.split())))
            return ocr_text
    return text


def _ocr_pdf_bytes(data: bytes) -> str:
    """OCR each page using PyMuPDF render + pytesseract (parallel)."""
    try:
        import fitz
        from PIL import Image
        import pytesseract
        import numpy as np
    except Exception as e:
        logger.warning("OCR dependencies not available: %s", e)
        return ""

    try:
        doc = fitz.open(stream=data, filetype="pdf")
    except Exception as e:
        logger.warning("fitz open failed: %s", e)
        return ""

    dpi          = int(os.getenv("OCR_DPI", "120"))
    zoom         = dpi / 72.0
    mat          = fitz.Matrix(zoom, zoom)
    max_pages    = int(os.getenv("OCR_MAX_PAGES", "0"))
    max_img_size = int(os.getenv("OCR_MAX_IMAGE_SIZE", "1600"))
    workers      = int(os.getenv("OCR_WORKERS", "4"))
    lang         = os.getenv("OCR_LANG", "chi_sim+eng")

    pages = list(doc)
    if max_pages > 0:
        pages = pages[:max_pages]

    # 渲染所有页为 numpy array（fitz 非线程安全，统一在主线程完成）
    page_arrays: list[tuple[int, np.ndarray]] = []
    for i, page in enumerate(pages):
        try:
            rotation = page.rotation
            if rotation:
                page.set_rotation(0)
            pix = page.get_pixmap(matrix=mat, alpha=False)
            if rotation:
                page.set_rotation(rotation)
            arr = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, 3)
            # 图像尺寸上限，防止超大页面拖慢 OCR
            if max_img_size > 0:
                h, w = arr.shape[:2]
                max_side = max(h, w)
                if max_side > max_img_size:
                    scale = max_img_size / max_side
                    new_h, new_w = int(h * scale), int(w * scale)
                    img = Image.fromarray(arr)
                    arr = np.array(img.resize((new_w, new_h), Image.LANCZOS))
                    logger.info("OCR page=%d resized %dx%d -> %dx%d", i, w, h, new_w, new_h)
            page_arrays.append((i, arr))
        except Exception as e:
            logger.warning("OCR render page=%d failed: %s", i, e)
    try:
        doc.close()
    except Exception:
        pass

    logger.info("OCR start pages=%d/%d dpi=%d engine=tesseract lang=%s workers=%d",
                len(page_arrays), len(pages), dpi, lang, workers)

    # pytesseract 每次调用启动独立子进程，天然线程安全，直接并行
    def ocr_page(args: tuple[int, np.ndarray]) -> tuple[int, str]:
        idx, arr = args
        try:
            from PIL import ImageOps, ImageFilter
            img = Image.fromarray(arr)
            # 图像预处理：灰度化 + 对比度拉伸 + 锐化，改善低 DPI 扫描件识别质量
            img = img.convert('L')
            img = ImageOps.autocontrast(img)
            img = img.filter(ImageFilter.SHARPEN)
            # 方向检测并自动旋转（修正扫描件 90/180/270° 旋转问题）
            try:
                osd = pytesseract.image_to_osd(img, output_type=pytesseract.Output.DICT)
                angle = osd.get("rotate", 0)
                if angle:
                    img = img.rotate(-angle, expand=True)
                    logger.info("OCR page=%d auto-rotated angle=%d", idx, angle)
            except Exception:
                pass  # osd.traineddata 不存在时跳过，不影响正常 OCR
            config = os.getenv("OCR_CONFIG", "--psm 6 --oem 3")
            text = pytesseract.image_to_string(img, lang=lang, config=config)
            oneline = " ".join(text.split())
            logger.info("OCR page=%d textLen=%d content=%s", idx, len(oneline),
                        (oneline[:300] + "...") if len(oneline) > 300 else oneline)
            return idx, text
        except Exception as e:
            logger.warning("OCR page=%d failed: %s", idx, e)
            return idx, ""

    results: dict[int, str] = {}
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for idx, text in pool.map(ocr_page, page_arrays):
            results[idx] = text

    return "\n".join(results.get(i, "") for i in range(len(page_arrays))).strip()


def _parse_docx_bytes(data: bytes) -> str:
    doc = Document(io.BytesIO(data))
    parts: list[str] = []
    for para in doc.paragraphs:
        if para.text:
            parts.append(para.text)
    return "\n".join(parts).strip()


def _bytes_to_text(data: bytes) -> str:
    for enc in ("utf-8", "utf-8-sig", "gb18030", "latin-1"):
        try:
            return data.decode(enc)
        except Exception:
            pass
    return ""
