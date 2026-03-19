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


_DHASH_BITS = 256   # 16×16 dHash


def _hamming(a: int, b: int) -> int:
    return bin(a ^ b).count("1")


def compare_page_hashes(hashes_a: list[int], hashes_b: list[int]) -> dict:
    """
    对两组页面哈希做最优匹配比对。
    对 A 中每一页，在 B 中找汉明距离最小的页，记录相似度。
    返回：平均相似度、高相似页数、逐页明细。
    """
    if not hashes_a or not hashes_b:
        return {"ok": False, "reason": "no_hashes"}

    HIGH_SIM = 0.90   # 汉明距离 < 10% 认为视觉高度相似

    page_details: list[dict] = []
    for i, ha in enumerate(hashes_a):
        best_dist = _DHASH_BITS
        best_j    = 0
        for j, hb in enumerate(hashes_b):
            d = _hamming(ha, hb)
            if d < best_dist:
                best_dist = d
                best_j    = j
        sim = round(1.0 - best_dist / _DHASH_BITS, 4)
        page_details.append({"pageA": i, "pageB": best_j, "sim": sim})

    avg_sim       = round(sum(p["sim"] for p in page_details) / len(page_details), 4)
    high_sim_pages = sum(1 for p in page_details if p["sim"] >= HIGH_SIM)

    return {
        "ok":           True,
        "avgPageSim":   avg_sim,
        "highSimPages": high_sim_pages,
        "totalPagesA":  len(hashes_a),
        "totalPagesB":  len(hashes_b),
        "pageDetails":  page_details,
    }


def overall_risk(comparisons: list[dict]) -> dict:
    """
    从多组两两对比结果中提炼一个综合风险结论。
    取所有有效对中"最可疑"那对作为依据，输出风险等级和关键指标。
    """
    valid = [c for c in comparisons if isinstance(c.get("result"), dict) and c["result"].get("ok")]
    if not valid:
        return {"riskLevel": "unknown", "riskLabel": "数据不足"}

    def _score(c: dict) -> float:
        r = c["result"]
        tfidf   = float(r.get("tfidfCosine") or 0)
        difflib = float(r.get("difflibRatio") or 0)
        longest = int(r.get("longestCommonRunChars") or 0)
        return tfidf * 0.5 + difflib * 0.3 + (0.2 if longest > 500 else 0.1 if longest > 100 else 0)

    worst = max(valid, key=_score)
    s = _score(worst)
    r = worst["result"]

    if s > 0.85:
        level, label = "high",   "高风险 · 疑似围标"
    elif s > 0.5:
        level, label = "medium", "中风险 · 建议人工复核"
    else:
        level, label = "low",    "低风险 · 未发现明显异常"

    return {
        "riskLevel":                level,
        "riskLabel":                label,
        "worstPairA":               worst.get("a") or f"文本{worst.get('i', '')}",
        "worstPairB":               worst.get("b") or f"文本{worst.get('j', '')}",
        "maxTfidfCosine":           round(float(r.get("tfidfCosine") or 0), 4),
        "maxDifflibRatio":          round(float(r.get("difflibRatio") or 0), 4),
        "maxLongestCommonRunChars": int(r.get("longestCommonRunChars") or 0),
        "maxCommonBlocksCount500+": int(r.get("commonBlocksCount500+") or 0),
    }


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

