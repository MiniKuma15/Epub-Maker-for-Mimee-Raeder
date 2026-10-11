# EPUB Maker OCR Service

Optional OCR backend used by the Android app. It accepts one rendered PDF page image per request; the Android app sends each page and combines the returned text.

## Available engines

- **paddleocr-v4**: PaddleOCR PP-OCRv4 Thai OCR, self-hosted.
- **paddleocr-v5**: PaddleOCR PP-OCRv5 Thai OCR, self-hosted (recommended to test first).
- **paddleocr**: backward-compatible alias for PP-OCRv5.
- **easyocr**: EasyOCR Thai + English, self-hosted.
- **paddleocr-vl**: document-oriented PaddleOCR-VL, self-hosted; needs more memory.
- **google-vision**: Google Cloud Vision DOCUMENT_TEXT_DETECTION, online.
- **gemini**: Google Gemini vision transcription, online.
- **qwen2.5-vl**: Qwen2.5-VL vision transcription through DashScope's OpenAI-compatible API.
- **gpt-4o**: OpenAI GPT-4o vision transcription.
- **claude-vision**: Anthropic Claude vision transcription.
- **google-document-ai**: Google Document AI OCR processor, online; requires a configured processor and Google Cloud credentials.

## Run locally

For lightweight online-only deployment (Google Vision, Gemini, Document AI, Qwen, GPT-4o and Claude), use Python 3.11 and install requirements-online.txt.

    pip install -r requirements-online.txt
    uvicorn main:app --host 0.0.0.0 --port 8000

For self-hosted OCR engines, install requirements.txt on a compatible machine. PaddleOCR dependencies are large and may not support every host.

- GET /health: health check.
- GET /engines: engine list.
- POST /ocr?engine=ENGINE_ID: upload an image using multipart field file.

Example:

    curl -F 'file=@page.png' 'http://localhost:8000/ocr?engine=google-vision'

## Docker deployment (online engines)

The included Dockerfile builds the lightweight online backend.

    docker build -t epub-maker-ocr ./ocr-backend
    docker run --rm -p 8000:8000 \
      -e OCR_API_TOKEN='replace-with-a-long-random-token' \
      -e GEMINI_API_KEY='your-key' epub-maker-ocr

Set OCR_API_TOKEN on the server to require a Bearer token for OCR requests. Enter the same token in the Android app's Server access token field. If OCR_API_TOKEN is unset, the endpoint has no built-in authentication; do not expose it publicly without a secure access layer.

## Google online setup

Set credentials as environment variables on the backend host, not in the Android APK or Git repository.

### Google Cloud Vision

1. Enable Cloud Vision API in a Google Cloud project and configure billing.
2. Create an API key restricted to the Cloud Vision API.
3. Set GOOGLE_CLOUD_VISION_API_KEY in the backend environment.

### Vision LLM providers

- **Qwen2.5-VL**: set `QWEN_API_KEY`. Optional: `QWEN_VL_MODEL` (defaults to `qwen2.5-vl-72b-instruct`) and `QWEN_BASE_URL` (defaults to the DashScope international endpoint).
- **OpenAI GPT-4o**: set `OPENAI_API_KEY`. Optional: `OPENAI_VISION_MODEL` (defaults to `gpt-4o`) and `OPENAI_BASE_URL` (defaults to `https://api.openai.com/v1`).
- **Anthropic Claude**: set `ANTHROPIC_API_KEY`. Optional: `CLAUDE_VISION_MODEL` and `ANTHROPIC_API_VERSION`.

These are server-side integrations. The APK only sends the selected page image to your configured OCR backend; provider keys must never be placed in Android source code. Provider availability, model names, quotas and billing depend on the account and may change.

Uses DOCUMENT_TEXT_DETECTION and language hints th, en. Usage may be billed; check current Google Cloud pricing and quota.

### Gemini API

1. Create an API key in Google AI Studio and check the model's availability, quotas, and pricing.
2. Set GEMINI_API_KEY on the backend.
3. Optionally set GEMINI_MODEL to a vision-capable model available to your API key. Default: gemini-2.5-flash.

Gemini is a multimodal generative model, not a dedicated OCR engine. It may reformat, omit, or infer text despite transcription instructions; proofread output carefully.

### Google Document AI

1. Enable Document AI API in a Google Cloud project and create an OCR processor.
2. Set GOOGLE_DOCUMENT_AI_PROJECT_ID, GOOGLE_DOCUMENT_AI_LOCATION, and GOOGLE_DOCUMENT_AI_PROCESSOR_ID.
3. Configure Application Default Credentials on the server (for example, GOOGLE_APPLICATION_CREDENTIALS pointing to a service-account JSON file) and grant the service account access to the processor.

Document AI may require billing and has processor-specific limits. Check current Google Cloud documentation and pricing.

## Security and privacy

Online Google engines send page images to Google APIs. Self-hosted engines send images only to the server you operate. Never expose this API publicly without HTTPS, authentication, rate limits, and privacy controls. OCR_API_TOKEN enables Bearer-token authentication; use a long random value and keep it private. Keep API keys and service-account credentials out of source code, logs, and the APK.

## Compatibility notes

- The app renders PDFs page-by-page and sends each page as a PNG. The backend currently supports page-image OCR, not raw multi-page PDF uploads.
- PaddleOCR PP-OCRv4/v5 and PaddleOCR-VL run on the backend, not natively on the Android device in this build. Their dependency compatibility depends on server OS, Python version, architecture, and memory. The app exposes each engine as a user-selectable option; it still needs a reachable backend and configured provider credentials.
- Code has been committed but still requires a real deployment and end-to-end tests against real Google credentials and sample PDFs.
