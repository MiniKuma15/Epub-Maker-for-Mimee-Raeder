# Epub Maker for Mimee Reader

Android app for importing PDFs, recognizing Thai/English text, editing the extracted text, and exporting EPUB3.

## Implemented in source (build/device verification pending)

- Light blue and white Jetpack Compose interface.
- PDF import using Android's document picker and PdfRenderer.
- Page-by-page Tesseract OCR configured for Thai + English.
- OCR text appears in an editable manuscript, with page markers and progress.
- EPUB3 export using the Android save dialog.

## OCR language data

The first OCR run downloads Tesseract's Thai and English traineddata files from the tessdata_fast repository into app-private storage. Internet is required for that initial download. OCR recognition itself runs on-device and does not upload PDF pages to an OCR service. The language files remain in the app's private storage for later offline runs.

## Limitations

- OCR output needs proofreading, especially for complex layouts and low-resolution scans.
- Current EPUB export creates one text chapter; it does not preserve PDF page layout, illustrations, or custom covers yet.
- The app has not been declared release-ready until CI build and real-device tests pass.

## Build

Requirements: JDK 17, Android SDK platform 34, Gradle 8.7.

```bash
gradle assembleDebug
```
