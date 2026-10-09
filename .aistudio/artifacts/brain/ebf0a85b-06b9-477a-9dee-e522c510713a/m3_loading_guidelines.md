# Material 3 Expressive Design System & Loading Guidelines

Official design principles and implementation specifications based on:
- [Building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)
- [Material Design 3 Loading Indicators](https://m3.material.io/components/loading-indicator/overview)
- [Material Design 3 Progress Indicators Guidelines](https://m3.material.io/components/progress-indicators/guidelines)

---

## 1. Material 3 Expressive Principles

- **Sculpted, Organic Geometry**: Containers abandon generic small radii in favor of **24dp–32dp extra-rounded expressive shapes** (`ExpressiveCardShape = 26.dp`, `ExpressiveDockShape = 32.dp`, `ExpressiveChipShape = 14.dp`).
- **Tactile Spring Physics**: Interactive elements respond to touch with spring-based motion curves (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMediumLow`) via `Modifier.expressiveBounce()`, providing immediate organic feedback.
- **High-Chroma Chromatic Contrast**: Built around the signature **Twitter Blue (`#1D9BF0`)** palette paired with rich celestial surface containers (`surfaceContainerLowest` through `surfaceContainerHighest`) for deep visual hierarchy in both dark and light themes.
- **Editorial Typography**: Confident display headers with prominent weights (`FontWeight.ExtraBold` / `Bold`) and tighter letter spacing (`-0.5.sp`) contrasting with high-legibility body copy.

---

## 2. Progress & Loading Indicators

### A. Circular Progress Indicators (`AstroM3CircularProgressIndicator`)
- **Visuals**: A full circular visible track in `surfaceVariant` with an active indicator in `primary` featuring rounded ends (`StrokeCap.Round`).
- **Modes**:
  - `progress == null`: Indeterminate loop with smooth rotational momentum along the fixed track.
  - `progress in 0f..1f`: Determinate progress animated via Compose `spring()`, optionally displaying centered or sub-label percentage.
- **Sizes**:
  - **Small (20-24dp)**: Inside action buttons (`PrimaryActionDock`), inline chips, and status badges.
  - **Medium (44dp)**: Card sections and dialogs.
  - **Large (64dp)**: Screen transitions and document rendering (`PdfViewerScreen`).

### B. Docked Linear Progress Indicator (`AstroM3DockedLinearProgress`)
- **Placement**: Docked directly beneath the `TopAppBar` or filter chips (Feed, Library, Explore).
- **Visuals**: 4dp height with `2dp` corner rounding and rounded indicator stroke ends.
- **Behavior**: Appears with `expandVertically() + fadeIn()` upon pull-to-refresh or background sync; dismisses smoothly with `shrinkVertically() + fadeOut()`.

### C. Skeleton Shimmer Placeholders (`AstroPaperCardSkeleton`, `AstroDetailsSkeleton`)
- **Visuals**: Extra-rounded 26dp card shapes matching the expected publication layout with continuous gradient sweep (`astroShimmer`).

---

## 3. Cold Start Zero-Flash Pattern
- Retains branded M3 splash screen until both session authentication and onboarding status settle.
- Never mounts navigation hosts on provisional routes, eliminating startup flickering.
