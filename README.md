# Epub Maker for Mimee Reader

Android app project for preparing text and exporting EPUB3 books, with OCR planned for later milestones.

## Current milestone

- Android Compose interface with a light blue and white theme.
- Editable book title, author, and manuscript text.
- Export edited text to an EPUB3 ZIP package using Android's document picker.
- Generates EPUB package metadata, navigation, XHTML chapter, and stylesheet.

## Not implemented yet

- PDF/image import and page-by-page OCR.
- Thai and English OCR engines.
- Multi-page layout preservation and image handling.
- Custom cover editor and automated APK release.

Do not treat OCR as available in this version. The current export flow expects text to be pasted or typed into the editor.

## Build

Requirements: JDK 17, Android SDK platform 34, and Gradle 8.7.

```bash
./gradlew assembleDebug
```

The debug APK, if the build succeeds, is created at `app/build/outputs/apk/debug/app-debug.apk`.
