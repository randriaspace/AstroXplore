# Material Design 3: Loading Indicators & Motion Guidelines

Official design principles and implementation specifications based on [Material Design 3 Loading Indicator Overview](https://m3.material.io/components/loading-indicator/overview) and [Guidelines](https://m3.material.io/components/loading-indicator/guidelines).

---

## 1. Core Principles

- **Predictable & Informative**: Loading states should provide immediate reassurance that the system is processing user requests. If an operation takes longer than 300ms, display a non-blocking indicator.
- **Expressive Motion**: Indicators use smooth easing curves (Standard Easing `CubicBezier(0.2, 0.0, 0.0, 1.0)`) and maintain consistent rotational momentum.
- **Hierarchical Placement**:
  - **Contextual / Inline**: Use linear progress indicators docked directly under Top App Bars or cards for background tasks (fetching, pushing, syncing).
  - **Content Skeleton (Shimmer)**: Use structural shimmer loaders matching the geometry of the target layout (cards, lists, details) instead of empty blank screens or spinners.
  - **Full-Screen / Modal**: Restrict centered spinners to full-screen transitions, initial authentication handshakes, and cold starts.

---

## 2. Component Types & Use Cases

### A. Circular Progress Indicator (`AstroM3LoadingIndicator`)
- **Visuals**: Indeterminate or determinate circular arc with contrasting track color (`surfaceVariant`) and indicator color (`primary`).
- **Variants**:
  - **Small (24dp)**: For buttons, inline status chips, and avatar badges.
  - **Medium (44dp)**: Standard card and section loaders.
  - **Large (64dp)**: Screen-level transitions and immersive splash states.
- **Accessibility**: Minimum touch and target clearance, high contrast ratio against backgrounds.

### B. Linear Progress Indicator (`AstroLinearProgressBar`)
- **Visuals**: Full-width or card-width horizontal bar with rounded stroke ends (`StrokeCap.Round`).
- **Positioning**: Attached to the bottom edge of `TopAppBar` or section headers.
- **Behavior**: Appears smoothly with fade-in/fade-out during background network syncs (pull-to-refresh, offline paper downloads, profile syncs). Never blocks touch interactions on content already on screen.

### C. Skeleton Shimmer Placeholders (`AstroPaperCardSkeleton`, `AstroDetailsSkeleton`)
- **Visuals**: Neutral shapes matching the expected content layout:
  - Header badge / Category pill
  - Title bars (2 lines with differing widths, e.g. 90% and 60%)
  - Author and metric chips
  - Abstract text lines
- **Motion**: Fluid, continuous gradient sweep (`rememberInfiniteTransition`) from `surfaceVariant` (alpha 0.4) to `surfaceVariant` (alpha 0.85) over 1200ms with linear easing.

---

## 3. Cold Start & Zero-Flash Transition Pattern

To prevent flickering between the Splash screen, Onboarding flow, and Main content feed:
1. **Synchronized Readiness Gate**: The splash screen remains opaque until **both** `SessionStatus` (Supabase/Auth) and `isOnboarded` (DataStore/Room) are non-null and settled.
2. **Atomic Destination Resolution**: `NavHost` is never mounted with an unverified start destination.
3. **Motion Cross-Fade**: When ready, the splash overlay dismisses via an alpha fade-out (`tween(400)`), revealing the target destination immediately without intermediate routing steps.
