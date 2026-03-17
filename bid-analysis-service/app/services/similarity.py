from __future__ import annotations

import difflib
from dataclasses import dataclass

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


def _normalize(s: str) -> str:
    return "".join(s.split())


def _tfidf_cosine(a: str, b: str) -> float:
    vec = TfidfVectorizer(analyzer="char", ngram_range=(2, 4), min_df=1)
    m = vec.fit_transform([a, b])
    return float(cosine_similarity(m[0], m[1])[0, 0])


def _difflib_ratio(a: str, b: str) -> float:
    return difflib.SequenceMatcher(a=a, b=b).ratio()


def _common_blocks(a: str, b: str, min_chars: int = 500) -> list[dict]:
    sm = difflib.SequenceMatcher(a=a, b=b)
    blocks = []
    for m in sm.get_matching_blocks():
        if m.size >= min_chars:
            snippet = a[m.a : m.a + min(200, m.size)]
            blocks.append({"size": m.size, "a_pos": m.a, "b_pos": m.b, "snippet": snippet})
    return blocks


def _count_matching_segments(a: str, b: str, min_chars: int = 50, gap_chars: int = 30) -> tuple[int, int]:
    """
    Count matching segments using difflib matching blocks.
    Returns: (segments_count, longest_segment_chars)
    """
    sm = difflib.SequenceMatcher(a=a, b=b)
    segs = 0
    longest = 0
    last_end_a = -10**9
    for m in sm.get_matching_blocks():
        if m.size <= 0:
            continue
        longest = max(longest, m.size)
        if m.size < min_chars:
            continue
        # de-dup close blocks on A side
        if m.a - last_end_a < gap_chars:
            last_end_a = max(last_end_a, m.a + m.size)
            continue
        segs += 1
        last_end_a = m.a + m.size
    return segs, longest


def compare_texts_dual(text_a: str, text_b: str) -> dict:
    a = _normalize(text_a or "")
    b = _normalize(text_b or "")
    if not a or not b:
        return {"ok": False, "reason": "empty text"}

    # When extracted text is too short (e.g. scanned PDF), similarity metrics are misleading.
    min_effective = 30
    if min(len(a), len(b)) < min_effective:
        return {
            "ok": False,
            "reason": "text_too_short",
            "minEffectiveChars": min_effective,
            "lenA": len(a),
            "lenB": len(b),
        }

    tfidf = _tfidf_cosine(a, b)
    ratio = _difflib_ratio(a, b)
    blocks = _common_blocks(a, b, min_chars=500)
    segs_50, longest = _count_matching_segments(a, b, min_chars=50, gap_chars=30)

    return {
        "ok": True,
        "tfidfCosine": tfidf,
        "difflibRatio": ratio,
        "commonBlocks500+": blocks,
        "commonBlocksCount500+": len(blocks),
        "matchingSegments50+": segs_50,
        "longestCommonRunChars": longest,
        "lenA": len(a),
        "lenB": len(b),
    }

