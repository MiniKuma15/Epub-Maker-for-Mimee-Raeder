# EPUB Maker OCR Service

Optional OCR backend used by the Android app. It accepts one rendered PDF page image per request; the Android app sends each page and combines the returned text.

## Available engines

- `paddleocr`: PaddleOCR PP-OCR, self-hosted.
- `easyocr`: EasyOCR Thai + English, self-hosted.
- `paddleocr-vl`: document-oriented PaddleOCR-VL, self-hosted; needs more memory.
- `google-vision`: Google Cloud Vision `DOCUMENT_TEXT_DETECTION`, online.
- `gemini`: Google Gemini vision transcription, online.
- `google-document-ai`: Google Document AI OCR processor, online; requires a configured processor and Google Cloud credentials.

## Run

Use Python 3.10 or 3.11 in a virtual environment. Install the dependencies suitable for your OS and hardware from `requirements.txt`, then:

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

- `GET /health`: health check.
- `GET /engines`: engine list.
- `POST /ocr?engine=ENGINE_ID`: upload an image using multipart field `file`.

Example:

```bash
curl -F 'file=@page.png' 'http://localhost:8000/ocr?engine=google-vision'
```

## Google online setup

Set credentials as environment variables on the backend host, not in the Android APK or Git repository.

### Google Cloud Vision

1. Enable Cloud Vision API in a Google Cloud project and configure billing.
2. Create an API key restricted to the Cloud Vision API.
3. Set `GOOGLE_CLOUD_VISION_API_KEY` in the backend environment.

Uses `DOCUMENT_TEXT_DETECTION` and language hints `th`, `en`. Usage may be billed; check current Google Cloud pricing and quota.

### Gemini API

1. Create an API key in Google AI Studio and check the model's availability, quotas, and pricing.
2. Set `GEMINI_API_KEY` on the backend.
3. Optionally set `GEMINI_MODEL` to a vision-capable model available to your API key. Default: `gemini-2.5-flash`.

Gemini is a multimodal generative model, not a dedicated OCR engine. It may reformat, omit, or infer text despite transcription instructions; proofread output carefully.

### Google Document AI

1. Enable Document AI API in a Google Cloud project and create an OCR processor.
2. Set `GOOGLE_DOCUMENT_AI_PROJECT_ID`, `GOOGLE_DOCUMENT_AI_LOCATION`, and `GOOGLE_DOCUMENT_AI_PROCESSOR_ID`.
3. Configure Application Default Credentials on the server (for example, `GOOGLE_APPLICATION_CREDENTIALS` pointing to a service-account JSON file) and grant the service account access to the processor.

Document AI may require billing and has processor-specific limits. Check current Google Cloud documentation and pricing.

## Security and privacy

The online Google engines send page images to Google APIs. The self-hosted engines send images only to the server you operate. Never expose this API publicly without HTTPS, authentication, rate limits, and privacy controls. Keep API keys and service-account credentials out of source code, logs, and the APK.

## Compatibility notes

- The app renders PDFs page-by-page and sends each page as a PNG. The backend currently supports page-image OCR, not raw multi-page PDF uploads.
- PaddleOCR/PaddleOCR-VL dependency compatibility depends on server OS, Python version, architecture, and memory.
- This code has been committed but has not yet been built or verified end-to-end against real Google credentials. Run smoke tests for each engine before production use.
