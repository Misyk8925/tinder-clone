# Photos service

Internal FastAPI media service. Profiles and Match keep their public APIs and
call this service for image validation, JPEG variants and S3 storage.

```bash
cd services/photos
pip install -r requirements-dev.txt
pytest
uvicorn app.main:app --host 0.0.0.0 --port 8070
```
