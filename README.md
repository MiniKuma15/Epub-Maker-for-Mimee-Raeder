# Epub Maker for Mimee Reader

Android app for importing PDFs, recognizing Thai/English text, editing the extracted text, and exporting EPUB3.

## OCR engines

The app now includes an OCR Engine selector:

- **Tesseract** — runs on the Android device after downloading Thai and English language data once; subsequent recognition can run offline.
- **PaddleOCR** — runs through the optional OCR backend.
- **EasyOCR** — runs through the optional OCR backend with Thai and English language support.
- **PaddleOCR-VL** — runs through the optional OCR backend and is intended for document-oriented recognition; it needs more server resources.

For the three remote engines, deploy the service in `ocr-backend/` and enter its HTTPS base URL in the app. The Android app sends rendered page images to that server one page at a time. Do not use a placeholder URL; remote OCR will fail until a server is running.

## Current app features

- Light blue and white Jetpack Compose interface.
- PDF import using Android's document picker and PdfRenderer.
- Page-by-page OCR with selectable local or remote engine.
- OCR text appears in an editable manuscript, with page markers and progress.
- EPUB3 export using the Android save dialog.

## OCR backend

See [ocr-backend/README.md](ocr-backend/README.md) for installation and API instructions.

The backend currently accepts one page image per request. Install and test each model runtime on the target server before relying on it. PaddleOCR-VL may need a high-memory machine. The service has no built-in authentication; do not expose it publicly without HTTPS, authentication, and suitable request limits.

## Limitations and verification status

- This update has not yet been built or tested on a physical Android device.
- OCR output needs proofreading, especially for complex layouts and low-resolution scans.
- Current EPUB export creates one text chapter; it does not preserve PDF page layout, illustrations, or custom covers.
- Remote OCR requires a separately deployed backend and network access. Tesseract remains the local/offline option.
- Do not treat this project as release-ready until CI build, backend tests, and real-device tests pass.

## Build

Requirements: JDK 17, Android SDK platform 34, Gradle 8.7.

```bash
gradle assembleDebug
```
