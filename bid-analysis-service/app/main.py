from __future__ import annotations

import logging
import os
import time
from pathlib import Path

# Load .env files at module level so worker processes (uvicorn reload) also get env vars
# override=True ensures .env values win over system environment variables
try:
    from dotenv import load_dotenv
    _svc_dir = Path(__file__).resolve().parents[1]
    load_dotenv(_svc_dir / ".env", override=True)
    load_dotenv(_svc_dir.parent / ".env", override=False)
except Exception:
    pass

print(f"[bid-analysis] TESSDATA_PREFIX={os.environ.get('TESSDATA_PREFIX', '(not set)')}", flush=True)

from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from app.services.parse import parse_upload_to_text
from app.services.similarity import compare_texts_dual
from app.services.price_patterns import analyze_price_patterns


app = FastAPI(title="Bid Analysis Service", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
logger = logging.getLogger("bid-analysis")


@app.get("/health")
def health():
    return {"ok": True}


class CompareRequest(BaseModel):
    texts: list[str] = Field(min_length=2, description="Plain texts to compare")
    prices: list[float] | None = Field(default=None, description="Optional bid prices for pattern checks")


@app.post("/analyze/compare-texts")
def analyze_compare_texts(req: CompareRequest):
    logger.info("compare-texts start count=%d", len(req.texts))
    comparisons = []
    n = len(req.texts)
    for i in range(n):
        for j in range(i + 1, n):
            res = compare_texts_dual(req.texts[i], req.texts[j])
            logger.info(
                "compare-texts pair i=%d j=%d lenA=%s lenB=%s tfidf=%s ratio=%s",
                i, j,
                res.get("lenA"), res.get("lenB"),
                f"{res.get('tfidfCosine'):.4f}" if res.get("ok") else "n/a",
                f"{res.get('difflibRatio'):.4f}" if res.get("ok") else "n/a",
            )
            comparisons.append({"i": i, "j": j, "result": res})

    price_result = analyze_price_patterns(req.prices) if req.prices else None
    logger.info("compare-texts done comparisons=%d", len(comparisons))
    return {"comparisons": comparisons, "pricePatterns": price_result}


@app.post("/analyze/compare-files")
async def analyze_compare_files(files: list[UploadFile] = File(...)):
    logger.info("compare-files start count=%d names=%s", len(files), [f.filename for f in files])
    texts: list[str] = []
    filenames: list[str] = []
    for f in files:
        filenames.append(f.filename or "unknown")
        texts.append(await parse_upload_to_text(f))

    comparisons = []
    n = len(texts)
    for i in range(n):
        for j in range(i + 1, n):
            res = compare_texts_dual(texts[i], texts[j])
            logger.info(
                "compare-files pair a=%s b=%s tfidf=%s ratio=%s",
                filenames[i], filenames[j],
                f"{res.get('tfidfCosine'):.4f}" if res.get("ok") else "n/a",
                f"{res.get('difflibRatio'):.4f}" if res.get("ok") else "n/a",
            )
            comparisons.append({"a": filenames[i], "b": filenames[j], "result": res})
    logger.info("compare-files done comparisons=%d", len(comparisons))
    return {"files": filenames, "comparisons": comparisons}


@app.post("/analyze/compare-two-files")
async def analyze_compare_two_files(a: UploadFile = File(...), b: UploadFile = File(...)):
    """
    Compare exactly two files and return a single similarity result.
    """
    logger.info("compare-two-files start a=%s b=%s", a.filename, b.filename)
    text_a = await parse_upload_to_text(a)
    text_b = await parse_upload_to_text(b)
    res = compare_texts_dual(text_a, text_b)
    logger.info(
        "compare-two-files done a=%s b=%s tfidf=%s ratio=%s",
        a.filename, b.filename,
        f"{res.get('tfidfCosine'):.4f}" if res.get("ok") else "n/a",
        f"{res.get('difflibRatio'):.4f}" if res.get("ok") else "n/a",
    )
    return {
        "a": a.filename or "a",
        "b": b.filename or "b",
        "result": res,
    }


class Base64File(BaseModel):
    filename: str
    content_b64: str = Field(description="Base64-encoded bytes")


class CompareBase64Request(BaseModel):
    files: list[Base64File] = Field(min_length=2)
    include_text_preview: bool = Field(default=True, description="Return extracted text preview per file")
    preview_chars: int = Field(default=0, ge=0, description="Max chars in text preview; 0 = full text")


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
            if req.preview_chars == 0:
                meta["textPreview"] = t.strip()
            else:
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

