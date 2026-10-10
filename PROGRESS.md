# Epub Maker — Development Progress

Last updated: 2026-10-10

## Milestone 1 — Editable text to EPUB3
Status: implemented in source; build and device testing not yet verified.

- [x] Thai-language Compose UI for title, author, and editable manuscript.
- [x] EPUB3 ZIP writer and Android document picker export.
- [ ] Validate generated EPUB in an EPUB reader.

## Milestone 2 — PDF import and Thai/English OCR
Status: implemented in source; build and device testing not yet verified.

- [x] Select PDF using Android's document picker.
- [x] Render PDF pages in order using Android PdfRenderer.
- [x] Integrate Tesseract OCR with Thai + English language selection.
- [x] Download language data on first use into app-private storage; subsequent recognition runs locally.
- [x] Append page markers and OCR text to the editable manuscript.
- [x] Show page progress and basic error messages.
- [ ] Run CI build and fix any dependency/API issues.
- [ ] Test on-device with text PDFs and scanned PDFs in Thai and English.
- [ ] Check memory use and recognition quality on large documents.

## Known limitations
- The first OCR run requires internet to download tha.traineddata and eng.traineddata; after download, OCR recognition is local.
- The PDF is OCR'd as page images. Text layout, illustrations, and original page design are not preserved in the EPUB yet.
- This source update has not yet been confirmed by a successful Android build or device test.
