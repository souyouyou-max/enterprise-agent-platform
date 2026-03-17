from __future__ import annotations

import io
import logging
import os
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
    logger.info(
        "parse done file=%s method=%s textLen=%d preview=%s",
        f.filename,
        _detect_method(name),
        text_norm_len,
        (text[:200] + "...").strip() if len(text) > 200 else text.strip(),
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

    # 2) OCR fallback for scanned PDFs (optional)
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
    """
    OCR each page using PyMuPDF render + pytesseract.
    Requires system `tesseract` installed and in PATH.
    """
    try:
        import fitz  # PyMuPDF
        import pytesseract
        from PIL import Image
    except Exception as e:
        logger.warning("OCR dependencies not available: %s", e)
        return ""

    try:
        doc = fitz.open(stream=data, filetype="pdf")
    except Exception as e:
        logger.warning("fitz open failed: %s", e)
        return ""

    dpi = int(os.getenv("OCR_DPI", "200"))
    zoom = dpi / 72.0
    mat = fitz.Matrix(zoom, zoom)

    lang = os.getenv("OCR_LANG", "chi_sim+eng")
    logger.info("OCR start pages=%d dpi=%d lang=%s", len(doc), dpi, lang)
    out_parts: list[str] = []
    for i, page in enumerate(doc):
        try:
            pix = page.get_pixmap(matrix=mat, alpha=False)
            img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
            page_text = pytesseract.image_to_string(img, lang=lang)
            logger.debug("OCR page=%d textLen=%d", i, len(page_text.strip()))
            out_parts.append(page_text)
        except Exception as e:
            logger.warning("OCR page=%d failed: %s", i, e)
            out_parts.append("")
    try:
        doc.close()
    except Exception:
        pass
    return "\n".join(out_parts).strip()


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


