# Epub Maker for Mimee Reader

Android app for importing PDFs, recognizing Thai/English text, editing extracted text, and exporting EPUB3.

## OCR engines in the app

- **Tesseract** — on-device/offline after Thai and English data download.
- **PaddleOCR** — self-hosted OCR backend.
- **EasyOCR** — self-hosted OCR backend.
- **PaddleOCR-VL** — self-hosted document-oriented OCR backend.
- **Google Cloud Vision OCR** — online Google OCR service.
- **Google Gemini Vision** — online multimodal model prompted to transcribe the page.
- **Google Document AI OCR** — online document OCR processor.

The three Google services need credentials configured on the OCR backend. They are not ready to use just by selecting them in the app. See [ocr-backend/README.md](ocr-backend/README.md) for setup.

## Important

- The app sends one rendered page image at a time to the configured OCR server for remote engines.
- Google online engines send the page image onward to Google. Check service terms, data handling, quota and pricing before processing copyrighted or private material.
- Keep API keys and Google service-account credentials on the backend; never embed them in the APK or commit them to Git.
- Remote OCR needs a deployed HTTPS backend. Tesseract is the only engine that runs locally in the app.
- This update has not yet been built or tested end-to-end with real Google credentials and sample PDFs. Verify each engine and inspect OCR results before treating the app as production-ready.
- The EPUB export currently creates a text-focused EPUB3; it does not preserve the PDF's original layout and illustrations.

## OCR backend

See [ocr-backend/README.md](ocr-backend/README.md) for installation, API routes, Google credentials, and deployment notes.
