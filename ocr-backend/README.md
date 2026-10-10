# EPUB Maker OCR Service

Optional server-side OCR service for the Android EPUB Maker app. This is separate from the on-device Tesseract engine.

## Engines

- `paddleocr`: PaddleOCR 3.x with Thai language configuration.
- `easyocr`: EasyOCR with Thai and English.
- `paddleocr-vl`: PaddleOCR-VL for document-oriented recognition. This engine needs substantially more memory than ordinary OCR and may be unsuitable for a small CPU-only instance.

## Run locally

Use Python 3.10 or 3.11 in a virtual environment. Install the dependencies appropriate to your operating system and hardware from `requirements.txt`, then run:

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

- `GET /health` checks that the API is running.
- `GET /engines` lists selectable engine IDs.
- `POST /ocr?engine=paddleocr`, `POST /ocr?engine=easyocr`, or `POST /ocr?engine=paddleocr-vl` accepts an image as multipart field `file`.

Example:

```bash
curl -F 'file=@page.png' 'http://localhost:8000/ocr?engine=easyocr'
```

## Important integration notes

- This service currently accepts one image per request, not a PDF. The Android app must render each PDF page to an image and send it to the selected remote engine.
- The Android app UI and networking have not yet been connected to this service. Until that integration and deployment are completed, these engines are not selectable or usable inside the APK.
- Do not expose this API publicly without HTTPS, authentication, request limits, and appropriate privacy controls. Uploaded pages may contain private book content.
- Model package compatibility varies by OS, CPU architecture, and available memory. Verify each engine on the actual deployment target before treating it as production-ready.
- The existing on-device Tesseract path remains independent and can continue working offline after its language data is downloaded.
