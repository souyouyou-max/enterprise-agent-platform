from __future__ import annotations

import io
import os
from fastapi import UploadFile
from pypdf import PdfReader
from docx import Document

from app.services.doc_legacy import parse_doc_bytes


async def parse_upload_to_text(f: UploadFile) -> str:
    data = await f.read()
    name = (f.filename or "").lower()
    if name.endswith(".pdf"):
        return _parse_pdf_bytes(data)
    if name.endswith(".docx"):
        return _parse_docx_bytes(data)
    if name.endswith(".doc"):
        t = parse_doc_bytes(data)
        return t if t else _bytes_to_text(data)
    # fallback
    return _bytes_to_text(data)


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
    if ocr_enabled and len("".join(text.split())) < min_len:
        ocr_text = _ocr_pdf_bytes(data)
        if ocr_text:
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
    except Exception:
        return ""

    try:
        doc = fitz.open(stream=data, filetype="pdf")
    except Exception:
        return ""

    dpi = int(os.getenv("OCR_DPI", "200"))
    zoom = dpi / 72.0
    mat = fitz.Matrix(zoom, zoom)

    lang = os.getenv("OCR_LANG", "chi_sim+eng")
    out_parts: list[str] = []
    for page in doc:
        try:
            pix = page.get_pixmap(matrix=mat, alpha=False)
            img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
            out_parts.append(pytesseract.image_to_string(img, lang=lang))
        except Exception:
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


