# Material 3 Progress Indicators Implementation Plan

Adopt the official [Material Design 3 Progress Indicators Guidelines](https://m3.material.io/components/progress-indicators/guidelines) across AstroXplore, delivering high-precision linear and circular progress indicators featuring continuous visible tracks, rounded stroke caps, and seamless transitions from indeterminate connection phases to determinate percentage tracking.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your selected preferences and the Material 3 guidelines:
> - **Indicator Placement**: Docked linear progress bars directly under Top App Bars across Feed, Library, and Explore, paired with circular indicators on cards and download actions.
> - **Indeterminate-to-Determinate Dynamic Transition**: Indicators start in indeterminate motion while negotiating network streams, then smoothly transition into determinate percentage progress as bytes download.
> - **Track Geometry & Styling**: Distinct, contrasting background track (`surfaceVariant`) with rounded stroke ends (`StrokeCap.Round`) and M3 standard easing transitions.

---

## 1. Overview & Core Concept

- **What It Does**: Implements official Material 3 Progress Indicators in Jetpack Compose, replacing basic spinners with track-anchored indicators. Provides instant visual feedback on progress state (connecting vs. active downloading) across paper fetching, offline PDF caching, and journal club syncing.
- **Target Audience / Persona**: Researchers downloading high-density astrophysics preprints and syncing collections, who need clear visual confirmation of download speed and remaining wait times.
- **Key Value**: 
  - Immediate feedback with visible track rings that demonstrate total capacity.
  - Zero jarring jumps between connecting and downloading states.
  - Consistent adherence to the Material Design 3 Progress Indicator guidelines.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Top App Bar Docked Linear Progress**:
   - In `LibraryScreen`, `FeedScreen`, and `ExploreScreen`, when a refresh or sync is triggered, a sleek 4dp rounded linear progress bar appears flush against the bottom of the TopAppBar.
   - The bar has a continuous track in `surfaceVariant` with a `primary` indicator flowing along it with rounded caps.
   - It animates out cleanly with a fade transition when the operation completes.

2. **Paper Download & PDF Viewer Progress**:
   - In `PaperDetailsScreen` and `PdfViewerScreen`:
     - **Phase 1 (Connecting)**: While establishing the OkHttp connection or querying arXiv, the circular indicator moves along a fixed visible track in indeterminate mode.
     - **Phase 2 (Streaming)**: As `DownloadPaperWorker` streams bytes, the indicator smoothly switches to determinate mode, filling the circular track from 0% to 100% with animated progress and percentage typography.
     - **Phase 3 (Completion)**: Replaces with a checkmark badge and ready status.

3. **Card-Level Circular Indicators**:
   - In list items (`ExpandedLibraryListItem`, `PaperCard`), download status pills display a 20dp compact circular progress indicator with track background and rounded caps.

### Visual Tokens & Specs
- **Indicator Color**: `MaterialTheme.colorScheme.primary`
- **Track Color**: `MaterialTheme.colorScheme.surfaceVariant` (contrasting, clearly visible)
- **Stroke Cap**: `StrokeCap.Round`
- **Linear Bar Height**: 4dp with 2dp corner rounding
- **Circular Sizes**: 
  - Compact / Card: 20dp (stroke 2.5dp)
  - Medium / Action Dock: 36dp (stroke 3.5dp)
  - Large / Reader: 56dp (stroke 4.5dp)

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified Dual-Mode Indicator (`AstroM3ProgressIndicator`)**
  - *Chosen Approach*: Build a unified component supporting both `progress = null` (indeterminate) and `progress = Float` (determinate) with animated cross-fades between the two states.
  - *Why*: Eliminates code duplication and allows screens to seamlessly switch states without recreating or recomposing layout nodes.
  - *Alternatives Considered*: Separate disconnected components for determinate and indeterminate (rejected because state transitions would flicker).

- **Decision 2: Top App Bar Linear Docking Pattern**
  - *Chosen Approach*: Dock `AstroM3DockedLinearProgress` directly beneath `TopAppBar` within `Scaffold` top bar slots.
  - *Why*: Perfectly matches the M3 guidelines showcased in your screenshot (`Episodes` top app bar reference) without shifting content or obscuring lists.
  - *Alternatives Considered*: Floating progress banner or bottom bar progress (rejected in favor of the official M3 TopAppBar docking standard).

---

## 4. Technical Architecture & Component Flow

```
┌────────────────────────────────────────────────────────┐
│                   AstroXplore Top Bar                  │
└──────────────────────────┬─────────────────────────────┘
                           │
             ┌─────────────▼─────────────┐
             │ AstroM3DockedLinearProgress│
             │   (Rounded Caps & Track)  │
             └─────────────┬─────────────┘
                           │
             ┌─────────────┴─────────────┐
             │   Indeterminate / Wait    │
             │ (Connecting to arXiv API) │
             └─────────────┬─────────────┘
                           │ Stream begins (contentLength known)
             ┌─────────────▼─────────────┐
             │    Determinate Progress   │
             │   (Smooth animated 0-100%)│
             └───────────────────────────┘
```

### Planned File Edits
1. **`core/ui/components/AstroLoadingIndicators.kt`**:
   - Add `AstroM3CircularProgressIndicator`: Dual-mode (determinate & indeterminate) with contrasting track and rounded ends.
   - Add `AstroM3DockedLinearProgress`: Top app bar docked linear indicator with track and animated progress.
   - Add `AstroCompactProgressBadge`: Inline card-level progress indicator.
2. **`features/feed/ui/PaperDetailsScreen.kt` & `PrimaryActionDock.kt`**:
   - Wire the dual-mode progress indicator to the PDF download state (transitioning from indeterminate connecting to determinate percentage).
3. **`features/library/ui/LibraryScreen.kt`**:
   - Dock `AstroM3DockedLinearProgress` below the library top app bar during sync operations.
4. **`features/library/ui/PdfViewerScreen.kt`**:
   - Integrate the determinate circular progress indicator showing numeric percentage during file retrieval.
