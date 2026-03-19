from __future__ import annotations

import hashlib
import io
import logging
import os
import threading
from concurrent.futures import ThreadPoolExecutor
from fastapi import UploadFile
from pypdf import PdfReader
from docx import Document

from app.services.doc_legacy import parse_doc_bytes

logger = logging.getLogger("bid-analysis.parse")

# --- OCR cache (方案一) ---
_ocr_cache: dict[str, str] = {}
_ocr_cache_lock = threading.Lock()

# --- Persistent thread pool (方案三) ---
_page_executor = ThreadPoolExecutor(max_workers=int(os.getenv("OCR_WORKERS", "4")))

# --- Engine lock for thread-safe singleton (方案二) ---
_engine_lock = threading.Lock()


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


def _get_rapidocr():
    """线程安全双重检查锁单例（方案二）。"""
    if not hasattr(_get_rapidocr, "_engine"):
        with _engine_lock:
            if not hasattr(_get_rapidocr, "_engine"):
                from rapidocr_onnxruntime import RapidOCR
                _get_rapidocr._engine = RapidOCR()
                logger.info("RapidOCR engine initialized")
    return _get_rapidocr._engine


def warmup_ocr():
    """启动预热：提前初始化 RapidOCR，消除首次请求延迟（方案二）。"""
    try:
        _get_rapidocr()
        logger.info("RapidOCR warmup done")
    except Exception as e:
        logger.warning("RapidOCR warmup failed: %s", e)


# ── 视觉感知哈希 ──────────────────────────────────────────

_DHASH_SIZE = 16          # 产生 16×16 = 256 bit 哈希
_DHASH_DPI  = 72          # 低分辨率渲染，速度优先


def compute_pdf_page_hashes(data: bytes) -> list[int]:
    """对 PDF 每页渲染缩略图并计算 dHash，返回每页的哈希整数列表。"""
    try:
        import fitz
        import numpy as np
        from PIL import Image
    except Exception as e:
        logger.warning("pHash dependencies not available: %s", e)
        return []

    try:
        doc = fitz.open(stream=data, filetype="pdf")
    except Exception as e:
        logger.warning("pHash fitz open failed: %s", e)
        return []

    zoom = _DHASH_DPI / 72.0
    mat  = fitz.Matrix(zoom, zoom)
    size = _DHASH_SIZE
    hashes: list[int] = []

    for i, page in enumerate(doc):
        try:
            pix = page.get_pixmap(matrix=mat, alpha=False)
            arr = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, 3)
            # 灰度缩放到 (size+1) × size，计算横向差分
            img    = Image.fromarray(arr).convert("L").resize((size + 1, size), Image.LANCZOS)
            pixels = np.array(img, dtype=float)
            diff   = pixels[:, :-1] > pixels[:, 1:]          # shape: (size, size)
            h      = int(sum(int(b) << idx for idx, b in enumerate(diff.flatten())))
            hashes.append(h)
        except Exception as e:
            logger.warning("pHash page=%d failed: %s", i, e)
            hashes.append(0)

    try:
        doc.close()
    except Exception:
        pass

    logger.info("pHash computed pages=%d", len(hashes))
    return hashes


def _ocr_pdf_bytes(data: bytes) -> str:
    """OCR each page using PyMuPDF render + RapidOCR (parallel, with SHA256 cache)."""
    # 方案一：查缓存
    key = hashlib.sha256(data).hexdigest()
    with _ocr_cache_lock:
        if key in _ocr_cache:
            logger.info("OCR cache hit sha256=%s", key[:12])
            return _ocr_cache[key]

    try:
        import fitz
        from PIL import Image
        import numpy as np
    except Exception as e:
        logger.warning("OCR dependencies not available: %s", e)
        return ""

    try:
        doc = fitz.open(stream=data, filetype="pdf")
    except Exception as e:
        logger.warning("fitz open failed: %s", e)
        return ""

    dpi          = int(os.getenv("OCR_DPI", "300"))
    zoom         = dpi / 72.0
    mat          = fitz.Matrix(zoom, zoom)
    max_pages    = int(os.getenv("OCR_MAX_PAGES", "0"))
    max_img_size = int(os.getenv("OCR_MAX_IMAGE_SIZE", "4000"))

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
            if max_img_size > 0:
                h, w = arr.shape[:2]
                max_side = max(h, w)
                if max_side > max_img_size:
                    scale = max_img_size / max_side
                    new_h, new_w = int(h * scale), int(w * scale)
                    arr = np.array(Image.fromarray(arr).resize((new_w, new_h), Image.LANCZOS))
                    logger.info("OCR page=%d resized %dx%d -> %dx%d", i, w, h, new_w, new_h)
            page_arrays.append((i, arr))
        except Exception as e:
            logger.warning("OCR render page=%d failed: %s", i, e)
    try:
        doc.close()
    except Exception:
        pass

    logger.info("OCR start pages=%d/%d dpi=%d engine=rapidocr",
                len(page_arrays), len(pages), dpi)

    engine = _get_rapidocr()

    def ocr_page(args: tuple[int, np.ndarray]) -> tuple[int, str]:
        idx, arr = args
        try:
            result, _ = engine(arr)
            if not result:
                return idx, ""
            text = "\n".join(line[1] for line in result)
            oneline = " ".join(text.split())
            logger.info("OCR page=%d textLen=%d content=%s", idx, len(oneline),
                        (oneline[:300] + "...") if len(oneline) > 300 else oneline)
            return idx, text
        except Exception as e:
            logger.warning("OCR page=%d failed: %s", idx, e)
            return idx, ""

    # 方案三：复用持久线程池（去掉 with ThreadPoolExecutor 每次新建/销毁）
    results: dict[int, str] = {}
    for idx, text in _page_executor.map(ocr_page, page_arrays):
        results[idx] = text

    result = "\n".join(results.get(i, "") for i in range(len(page_arrays))).strip()

    # 方案一：写缓存
    with _ocr_cache_lock:
        _ocr_cache[key] = result
    return result


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
