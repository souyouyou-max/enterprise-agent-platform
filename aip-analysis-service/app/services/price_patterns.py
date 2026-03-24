from __future__ import annotations

import math
from collections import Counter


def analyze_price_patterns(prices: list[float]) -> dict:
    if not prices:
        return {"ok": False, "reason": "empty"}
    xs = [float(x) for x in prices if x is not None]
    if len(xs) < 2:
        return {"ok": False, "reason": "need >=2 prices"}

    # basic stats
    mean = sum(xs) / len(xs)
    var = sum((x - mean) ** 2 for x in xs) / (len(xs) - 1)
    stdev = math.sqrt(var) if var > 0 else 0.0

    # exact duplicates (rounded to cents)
    rounded = [round(x, 2) for x in xs]
    dup = {k: v for k, v in Counter(rounded).items() if v >= 2}

    # concentration: top-1 frequency ratio
    most_common = Counter(rounded).most_common(1)[0]
    top1_ratio = most_common[1] / len(rounded)

    return {
        "ok": True,
        "count": len(xs),
        "mean": mean,
        "stdev": stdev,
        "duplicates": dup,
        "top1Ratio": top1_ratio,
    }

