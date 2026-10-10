# Epub Maker for Mimee Reader

Android app for importing PDFs, recognizing Thai/English text, editing extracted text, and exporting EPUB3.

## Current app features

- Import PDF through Android's document picker and render each page for OCR.
- Local/offline Tesseract OCR after downloading Thai and English language data.
- Optional server OCR: PaddleOCR, EasyOCR, PaddleOCR-VL, Google Cloud Vision, Gemini Vision, and Google Document AI.
- OCR progress display and editable manuscript text.
- Book title and author metadata.
- Draft autosave for the manuscript and OCR server settings.
- EPUB3 export with a navigation table of contents.
- Split EPUB chapters by adding a line that starts with # followed by a chapter title. Example: # Chapter One.
- Android debug APK build workflow at .github/workflows/android-build.yml.

## OCR backend

Online and self-hosted OCR engines require a running backend. For lightweight online engines, the backend Dockerfile and requirements-online.txt are provided under ocr-backend. See ocr-backend/README.md for deployment, Google credentials, pricing, and privacy details.

Set OCR_API_TOKEN on the backend to require Bearer-token authentication, then enter the same token in the app's Server access token field. Use HTTPS for any server exposed over the internet. Never put Google API keys or service-account credentials in the APK or Git repository.

## Important limitations

- The Google services are not ready merely by selecting them in the app; the backend must be deployed and the relevant credentials configured.
- Google OCR can incur charges and sends page images to Google. Review the provider's current terms, quota, and pricing.
- Tesseract downloads its Thai and English data files the first time it is used.
- EPUB output is a reflowable text book; it does not preserve the source PDF's original page layout or illustrations.
- Chapter splitting is currently based on explicit # Heading lines in the editable manuscript.
- The repository changes have not yet been verified by a successful Android build or real end-to-end OCR tests. Treat build and runtime verification as remaining acceptance checks.

## Build

Pushes to main trigger the Android build workflow. The debug APK is uploaded as the epub-maker-debug-apk workflow artifact when the build succeeds.
