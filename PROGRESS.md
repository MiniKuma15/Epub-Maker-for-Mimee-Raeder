# Epub Maker — Development Progress

Last updated: 2026-10-10

## Milestone 1 — Editable text to EPUB3

Status: implemented in source; build and device testing not yet verified.

- [x] Add Android Gradle project files and Compose dependencies.
- [x] Add a Thai-language UI for book title, author, and editable manuscript.
- [x] Implement EPUB3 ZIP output with `mimetype`, `META-INF/container.xml`, OPF metadata, navigation XHTML, chapter XHTML, and CSS.
- [x] Use Android's CreateDocument flow so the user chooses where to save the EPUB.
- [ ] Run Gradle build and fix any compilation issues.
- [ ] Test the generated EPUB in an EPUB reader and validate its package structure.

## Next milestones

1. PDF/image import and page handling.
2. Thai + English OCR integration, including a reliable offline model/data distribution strategy.
3. Review and edit OCR output while preserving page order.
4. Cover selection/customization and EPUB image handling.
5. End-to-end testing and release APK workflow.

## Accuracy notes

The app does **not** perform OCR yet. EPUB export currently converts text entered into the editor into one XHTML chapter. No APK build or EPUB reader validation has been claimed.
