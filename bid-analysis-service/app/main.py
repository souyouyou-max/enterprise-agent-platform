from __future__ import annotations

import logging
import time

from fastapi import FastAPI, File, UploadFile
from pydantic import BaseModel, Field

from app.services.parse import parse_upload_to_text
from app.services.similarity import compare_texts_dual
from app.services.price_patterns import analyze_price_patterns


app = FastAPI(title="Bid Analysis Service", version="0.1.0")
logger = logging.getLogger("bid-analysis")


@app.get("/health")
def health():
    return {"ok": True}


class CompareRequest(BaseModel):
    texts: list[str] = Field(min_length=2, description="Plain texts to compare")
    prices: list[float] | None = Field(default=None, description="Optional bid prices for pattern checks")


@app.post("/analyze/compare-texts")
def analyze_compare_texts(req: CompareRequest):
    comparisons = []
    n = len(req.texts)
    for i in range(n):
        for j in range(i + 1, n):
            comparisons.append(
                {
                    "i": i,
                    "j": j,
                    "result": compare_texts_dual(req.texts[i], req.texts[j]),
                }
            )

    price_result = analyze_price_patterns(req.prices) if req.prices else None
    return {"comparisons": comparisons, "pricePatterns": price_result}


@app.post("/analyze/compare-files")
async def analyze_compare_files(files: list[UploadFile] = File(...)):
    texts: list[str] = []
    filenames: list[str] = []
    for f in files:
        filenames.append(f.filename or "unknown")
        texts.append(await parse_upload_to_text(f))

    comparisons = []
    n = len(texts)
    for i in range(n):
        for j in range(i + 1, n):
            comparisons.append(
                {
                    "a": filenames[i],
                    "b": filenames[j],
                    "result": compare_texts_dual(texts[i], texts[j]),
                }
            )
    return {"files": filenames, "comparisons": comparisons}


@app.post("/analyze/compare-two-files")
async def analyze_compare_two_files(a: UploadFile = File(...), b: UploadFile = File(...)):
    """
    Compare exactly two files and return a single similarity result.
    """
    text_a = await parse_upload_to_text(a)
    text_b = await parse_upload_to_text(b)
    return {
        "a": a.filename or "a",
        "b": b.filename or "b",
        "result": compare_texts_dual(text_a, text_b),
    }


class Base64File(BaseModel):
    filename: str
    content_b64: str = Field(description="Base64-encoded bytes")


class CompareBase64Request(BaseModel):
    files: list[Base64File] = Field(min_length=2)
    include_text_preview: bool = Field(default=True, description="Return extracted text preview per file")
    preview_chars: int = Field(default=800, ge=100, le=5000, description="Max chars in text preview")


@app.post("/analyze/compare-base64")
def analyze_compare_base64(req: CompareBase64Request):
    import base64
    import hashlib

    t0 = time.perf_counter()
    texts: list[str] = []
    names: list[str] = []
    sizes: list[int] = []
    sha256s: list[str] = []
    for f in req.files:
        names.append(f.filename)
        data = base64.b64decode(f.content_b64)
        sizes.append(len(data))
        sha256s.append(hashlib.sha256(data).hexdigest())
        # reuse parse logic by mimicking filename extension
        # (keep this local to avoid file I/O)
        from app.services.parse import _parse_pdf_bytes, _parse_docx_bytes, _bytes_to_text
        low = f.filename.lower()
        if low.endswith(".pdf"):
            texts.append(_parse_pdf_bytes(data))
        elif low.endswith(".docx"):
            texts.append(_parse_docx_bytes(data))
        else:
            texts.append(_bytes_to_text(data))

    logger.info(
        "compare-base64 start files=%d totalBytes=%d names=%s",
        len(names),
        sum(sizes),
        names,
    )

    file_metas = []
    for i in range(len(names)):
        t = texts[i] or ""
        t_norm = "".join(t.split())
        meta = {
            "name": names[i],
            "sizeBytes": sizes[i],
            "sha256": sha256s[i],
            "textLen": len(t_norm),
        }
        if req.include_text_preview:
            meta["textPreview"] = (t[: req.preview_chars] + ("..." if len(t) > req.preview_chars else "")).strip()
        file_metas.append(meta)

    comparisons = []
    n = len(texts)
    for i in range(n):
        for j in range(i + 1, n):
            res = compare_texts_dual(texts[i], texts[j])
            comparisons.append({"a": names[i], "b": names[j], "result": res})

            if isinstance(res, dict) and res.get("ok") is True:
                logger.info(
                    "pair a=%s b=%s tfidf=%.4f ratio=%.4f longest=%s seg50=%s blocks500=%s",
                    names[i],
                    names[j],
                    float(res.get("tfidfCosine") or 0.0),
                    float(res.get("difflibRatio") or 0.0),
                    res.get("longestCommonRunChars"),
                    res.get("matchingSegments50+"),
                    res.get("commonBlocksCount500+"),
                )
            else:
                reason = res.get("reason") if isinstance(res, dict) else None
                logger.info("pair a=%s b=%s not_ok reason=%s", names[i], names[j], reason)

    logger.info("compare-base64 done comparisons=%d elapsedMs=%.2f", len(comparisons), (time.perf_counter() - t0) * 1000.0)
    return {"files": names, "fileMetas": file_metas, "comparisons": comparisons}


class CompareTwoTextsRequest(BaseModel):
    a: str
    b: str


@app.post("/analyze/compare-two-texts")
def analyze_compare_two_texts(req: CompareTwoTextsRequest):
    return {"result": compare_texts_dual(req.a, req.b)}

