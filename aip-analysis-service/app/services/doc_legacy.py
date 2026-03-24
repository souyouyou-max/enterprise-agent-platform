from __future__ import annotations

import tempfile
from pathlib import Path


def parse_doc_bytes(data: bytes) -> str:
    """
    Best-effort legacy .doc parser.
    Strategy:
    - write to temp file
    - try textract (if available)
    - fallback to empty string
    """
    try:
        import textract  # type: ignore
    except Exception:
        return ""

    with tempfile.TemporaryDirectory() as td:
        p = Path(td) / "file.doc"
        p.write_bytes(data)
        try:
            out = textract.process(str(p))
            return out.decode("utf-8", errors="ignore").strip()
        except Exception:
            return ""

