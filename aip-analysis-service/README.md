# Bid Analysis Service (FastAPI)

## Run

```bash
cd bid-analysis-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8099 --reload
```

## Endpoints

- `GET /health`
- `POST /analyze/compare-files` (multipart, multiple files)
- `POST /analyze/compare-two-files` (multipart, exactly 2 files: a,b)
- `POST /analyze/compare-texts` (JSON, list of texts)
- `POST /analyze/compare-two-texts` (JSON, exactly 2 texts)

