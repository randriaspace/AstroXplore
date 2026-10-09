# PDF Reader & External Paper Flow Implementation Plan

Enhance the publication reading flow in AstroXplore to distinguish between downloadable PDFs and external publisher links, ensure the action button is disabled while downloading and auto-opens the PDF reader upon completion, and equip the PDF viewer with floating zoom controls, page scrubber, and native Android printing.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed based on your requirements:
> - **External vs. Downloadable Flow**: If a paper does not have a downloadable PDF link, the primary action button adapts to **"Open Publisher Link"** with an external link icon (`OpenInNew`) to launch the publisher / ADS web portal.
> - **Download Button Lock & Auto-Open**: While downloading, the "Read PDF" button is non-clickable (`enabled = false`) and displays the Material 3 progress indicator. As soon as the download finishes, the app **automatically opens the PDF reader**.
> - **PDF Viewer Toolkit**: Equipped with floating zoom controls (`+`, `-`, reset to 100%), pinch-to-zoom, a page scrubber slider (`Page X of Y`), and a top-app-bar **Print** action using Android `PrintManager`.

---

## 1. Overview & Core Concept

- **What It Does**: Refines the primary action button in `PaperDetailsScreen` to provide appropriate affordances based on whether the paper has an open-access PDF (arXiv) or only an external publisher identifier (DOI / ADS). Eliminates tap confusion during background downloads by locking the button and auto-transitioning to the reader once downloaded. Enriches `PdfViewerScreen` with zoom, page navigation, and document printing.
- **Target Audience / Persona**: Astrophysicists reading dense multi-page preprints who need to inspect figures with zoom, print research documents, and easily open paywalled or publisher articles.
- **Key Value**:
  - Clear user expectations: users know immediately if a paper can be read offline or needs an external browser.
  - Zero friction: no need to tap "Read Offline" again after waiting for a download to finish.
  - Full reading utility: zoom in on charts/equations, jump through pages, and print directly from the device.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Paper Details Action Flow**:
   - **Case A: Downloadable PDF (Not Downloaded Yet)**:
     - Button displays `[Download PDF / Read PDF]` in primary color.
     - User taps button: triggers `downloadPaperWorker`.
     - Button immediately disables (`enabled = false`), transitioning to M3 progress tracking: `"Connecting..."` -> `"Saving (X%)"`.
     - Once complete, `LaunchedEffect` detects completion and **automatically opens the PDF Viewer**.
   - **Case B: Downloadable PDF (Already Cached)**:
     - Button displays `[Read Offline]` with a checkmark badge. Tapping opens the viewer immediately.
   - **Case C: External Link Only (No PDF URL)**:
     - Button adapts to `[Open Publisher Link]` with `Icons.AutoMirrored.Outlined.OpenInNew`.
     - Tapping opens the DOI or NASA ADS link in the system browser or custom tab.

2. **Enhanced PDF Viewer (`PdfViewerScreen`)**:
   - **Top App Bar**:
     - Document title and "Offline Storage" chip.
     - **Print Action** (`Icons.Outlined.Print`): invokes Android `PrintManager` using standard `PrintDocumentAdapter`.
     - **Share Action** (`Icons.Default.Share`): shares PDF file via `FileProvider`.
   - **Reader Canvas**:
     - Pinch-to-zoom gesture and double-tap reset.
     - Page list renders smoothly with zoomed resolution.
   - **Floating Zoom Controls**:
     - Compact pill in bottom-right with Zoom Out (`-`), Current Zoom (`100%` / `150%`), and Zoom In (`+`).
   - **Page Scrubber**:
     - Bottom bar showing `Page 3 of 28` with a discrete slider for jumping across long papers.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native Android `PrintManager` vs. Third-Party PDF Exporters**
  - *Chosen Approach*: Implement a lightweight custom `PrintDocumentAdapter` passing the local cached PDF file directly to Android's system `PrintManager`.
  - *Why*: Zero third-party dependencies, supports physical WiFi printers, Save-as-PDF, and Google Cloud Print with 100% standard OS compliance.
  - *Alternatives Considered*: Opening an external PDF viewer app to print (rejected because it forces the user out of AstroXplore).

- **Decision 2: Automatic Transition on Download Finish**
  - *Chosen Approach*: Observe `downloadState` in `PaperDetailsScreen`. When `downloadState` transitions from `DOWNLOADING` to `DOWNLOADED` while the screen is active, trigger `onReadPdfClick` automatically.
  - *Why*: Directly addresses user feedback that clicking during download is impossible and makes the download-to-read transition completely seamless.

---

## 4. Technical Architecture & Component Flow

```
                     ┌───────────────────────────────┐
                     │     Paper Details Screen      │
                     └───────────────┬───────────────┘
                                     │
                    Has PDF URL? ────┴──── No PDF URL?
                   /                                   \
                  ▼                                     ▼
        ┌───────────────────┐                 ┌───────────────────┐
        │ [Read / Save PDF] │                 │ [Open Publisher]  │
        └─────────┬─────────┘                 └─────────┬─────────┘
                  │ Tapped                              │ Tapped
                  ▼                                     ▼
        ┌───────────────────┐                 ┌───────────────────┐
        │ Button Disabled & │                 │ Open Browser / ADS│
        │ M3 Progress Bar   │                 └───────────────────┘
        └─────────┬─────────┘
                  │ Download Complete
                  ▼
        ┌───────────────────┐
        │ Auto-Open Reader  │
        └─────────┬─────────┘
                  ▼
        ┌────────────────────────────────────────────────────────┐
        │                   PdfViewerScreen                      │
        │  [Print Icon]  [Share Icon]  [Pinch & Floating Zoom]   │
        │                  [Page Scrubber]                       │
        └────────────────────────────────────────────────────────┘
```

### Planned File Edits
1. **`core/ui/components/PrimaryActionDock.kt`**:
   - Add support for `hasPdfUrl: Boolean`.
   - When `!hasPdfUrl`: render "Open Publisher" with `Icons.AutoMirrored.Outlined.OpenInNew`.
   - When `downloadState == DownloadState.DOWNLOADING`: disable button (`enabled = false`) and render M3 progress.
2. **`features/feed/ui/PaperDetailsScreen.kt`**:
   - Pass `hasPdfUrl = !paper.pdfUrl.isNullOrBlank()` to `PrimaryActionDock`.
   - Add `LaunchedEffect(downloadState)` to automatically open `onReadPdfClick` when download completes.
3. **`features/library/ui/PdfViewerScreen.kt`**:
   - Add floating zoom controls (`+`, `-`, reset).
   - Implement pinch-to-zoom using `graphicsLayer` scale and translation.
   - Add page scrubber slider.
   - Implement `printPdfDocument(context, file)` using Android `PrintManager`.
