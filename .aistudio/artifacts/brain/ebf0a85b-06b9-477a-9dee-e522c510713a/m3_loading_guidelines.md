# Material Design 3: Loading & Progress Indicators Guidelines

Official design principles and implementation specifications based on:
- [Material Design 3 Loading Indicators](https://m3.material.io/components/loading-indicator/overview)
- [Material Design 3 Progress Indicators Guidelines](https://m3.material.io/components/progress-indicators/guidelines)

---

## 1. Core Principles

- **Predictable & Informative**: Progress indicators inform users about the ongoing state of a process (e.g. streaming, calculating, syncing).
- **Determinate vs. Indeterminate**:
  - **Determinate**: Used when the wait time or file size is known. Fills continuously along a visible track from 0% to 100%.
  - **Indeterminate**: Used when wait time or total size is unknown. Moves along a fixed visible track, growing and shrinking in size.
  - **Transition**: As more information about a process becomes available (e.g. download content-length resolved), the indicator smoothly transitions from **indeterminate** to **determinate**.
- **Track & Stroke Geometry**: Always retain a contrasting, visible background track (`surfaceVariant`) with rounded stroke caps (`StrokeCap.Round`).

---

## 2. Component Types & Placement

### A. Circular Progress Indicators (`AstroM3CircularProgressIndicator`)
- **Visuals**: A full circular visible track in `surfaceVariant` with an active indicator in `primary` featuring rounded ends (`StrokeCap.Round`).
- **Modes**:
  - `progress == null`: Indeterminate loop with smooth momentum along the fixed track.
  - `progress in 0f..1f`: Determinate progress animated via Compose `spring()`, optionally displaying centered or sub-label percentage.
- **Sizes**:
  - **Small (20-24dp)**: Inside action buttons (`PrimaryActionDock`), inline chips, and status badges.
  - **Medium (44dp)**: Card sections and dialogs.
  - **Large (64dp)**: Screen transitions and document rendering (`PdfViewerScreen`).

### B. Docked Linear Progress Indicator (`AstroM3DockedLinearProgress`)
- **Placement**: Docked directly beneath the `TopAppBar` or filter chips (Feed, Library, Explore).
- **Visuals**: 4dp height with `2dp` corner rounding and rounded indicator stroke ends.
- **Behavior**: Appears with `expandVertically() + fadeIn()` upon pull-to-refresh or background sync; dismisses smoothly with `shrinkVertically() + fadeOut()`. Content beneath remains interactable.

### C. Skeleton Shimmer Placeholders (`AstroPaperCardSkeleton`, `AstroDetailsSkeleton`)
- **Visuals**: Neutral card shapes matching the expected publication layout with continuous gradient sweep (`astroShimmer`).

---

## 3. Cold Start Zero-Flash Pattern
- Retains branded M3 splash screen until both session authentication and onboarding status settle.
- Never mounts navigation hosts on provisional routes, eliminating startup flickering.
