# Epub Maker for Mimee Reader

Android EPUB 3 maker built with Kotlin, Jetpack Compose and MVVM.

## Current implementation

- Manual conversion: selecting a file does not start OCR or conversion. Processing begins only when the user taps **Start Conversion**.
- Input: PDF, JPG and PNG via Android's Storage Access Framework.
- PDF text extraction using PDFBox-Android, with page-by-page rendering for scanned pages.
- Offline OCR using Tesseract for Thai and English; Google ML Kit bundled Latin OCR is also available for Latin-script text. ML Kit's Latin model does not support Thai directly.
- Online OCR server choices: PaddleOCR, EasyOCR, PaddleOCR-VL, Google Cloud Vision, Gemini Vision and Google Document AI. Online providers require a deployed backend and server-side credentials.
- Structured EPUB domain model: Book, Chapter, TextBlock, ImageBlock, RichTextRun, ParagraphStyle, BookMetadata and CoverImage.
- Rich text editor: bold, italic, alignment, first-line indentation and paragraph spacing.
- Scanned page retention: pages with no embedded text can be embedded as images instead of OCR. Imported images can be kept alongside OCR text.
- Image Alt Text and Caption editing.
- Cover selection from the first page of a PDF or a JPG/PNG image.
- EPUB 3 export with XHTML chapters, CSS, navigation, metadata, embedded images and cover manifest.
- GitHub Actions builds a debug APK and uploads it as the `epub-maker-debug-apk` artifact.

## OCR backend

Online and self-hosted OCR engines require the backend under `ocr-backend`. See `ocr-backend/README.md` for deployment and credentials. Set `OCR_API_TOKEN` on the backend to require Bearer-token authentication. Use HTTPS for exposed servers. Never store Google API keys or service-account credentials in the APK or Git repository.

## Build and verification

Pushes to `main` trigger `.github/workflows/android-build.yml`. The workflow runs Gradle unit-test compilation and builds `app/build/outputs/apk/debug/app-debug.apk`; successful runs upload the APK artifact. The build is considered verified only after the workflow reports success. A successful compile alone does not replace testing on real PDFs, Thai OCR samples, image-only inputs and EPUB reader apps.

## Notes

- OCR accuracy depends on scan quality, fonts, layout and the selected engine.
- Google OCR can incur charges and transmits page images to the selected provider.
- Tesseract downloads Thai and English language data the first time it runs; after the data is cached, OCR runs locally.
- The editor preserves inline rich-text runs in the EPUB model. The EPUB is reflowable and does not attempt to reproduce the exact PDF page layout.


Build validation branch created to run the repository's GitHub Actions checks against the current app sources. The validation commit was refreshed after fixing the initial compiler errors.
