import os
import logging
from pathlib import Path

import uvicorn
from app.core.config import settings

if __name__ == "__main__":
    logging.basicConfig(
        level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    logging.getLogger("bid-analysis").setLevel(getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO))

    # Load env from repo root .env (optional)
    try:
        from dotenv import load_dotenv

        repo_root = Path(__file__).resolve().parents[1]
        load_dotenv(repo_root / ".env", override=False)
    except Exception:
        pass

    # OCR defaults (can be overridden by real env vars)
    os.environ.setdefault("OCR_ENABLED", "true")
    os.environ.setdefault("OCR_MIN_TEXT_LEN", "800")
    os.environ.setdefault("OCR_LANG", "chi_sim+eng")

    uvicorn.run(
        "app.main:app",
        host="127.0.0.1",
        port=8099,
        reload=settings.DEBUG,
        log_level=settings.LOG_LEVEL.lower(),
        access_log=True,
    ) 