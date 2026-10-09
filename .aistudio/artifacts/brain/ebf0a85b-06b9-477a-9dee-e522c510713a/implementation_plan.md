# Material 3 Expressive Design System Upgrade Implementation Plan

Transform AstroXplore from baseline Material 3 to **Material 3 Expressive** based on the official [Building with M3 Expressive Guidelines](https://m3.material.io/blog/building-with-m3-expressive), delivering a confident, vibrant scientific research experience with the signature Twitter Blue chromatic palette, 24dp–32dp extra-rounded containers, spring-based bounce physics, and punchy editorial typography.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your selected preferences and the M3 Expressive guidelines:
> - **Color Intensity**: Preserving the signature **Twitter Blue (`#1D9BF0`)** as the primary chromatic anchor, expanded into rich cosmic surface containers (`surfaceContainerLowest` through `surfaceContainerHighest`) with subtle celestial tints.
> - **Container Geometry**: Upgrading all cards, dialogs, bottom sheets, and action docks to **24dp–32dp extra-rounded expressive shapes** (`Shapes.kt` system).
> - **Interaction Physics**: Introducing **Spring-based physics with subtle organic bounce** (`Modifier.expressiveBounce()`) on cards, buttons, and floating docks using `Spring.DampingRatioMediumBouncy` and `Spring.StiffnessMediumLow`.
> - **Expressive Typography**: Bold, editorial display headers with tighter tracking and high typographic contrast.

---

## 1. Overview & Core Concept

- **What It Does**: Upgrades AstroXplore's visual language, token system, component shapes, and motion engine to Google's latest **Material 3 Expressive** standard. Replaces generic boxy surfaces with sculpted, organic 24dp–32dp containers, responsive tactile spring physics, and high-chroma tonal contrasts.
- **Target Audience / Persona**: Astrophysicists and researchers who want an engaging, modern reading tool that feels fluid, tactile, and distinct from traditional utilitarian research databases.
- **Key Value**:
  - Tactile delight through organic spring physics on touches and taps.
  - Instantly recognizable visual hierarchy with expressive 24dp–32dp rounded containers.
  - Deep chromatic contrast anchored around the signature Twitter Blue palette.

---

## 2. Visual Tokens & Theming Upgrades

### A. Shapes (`Shapes.kt`)
- **Extra Small**: `RoundedCornerShape(8.dp)` (Badges, tags)
- **Small**: `RoundedCornerShape(12.dp)` (Filter chips, inputs)
- **Medium**: `RoundedCornerShape(18.dp)` (Buttons, compact cards)
- **Large**: `RoundedCornerShape(26.dp)` (Publication cards, dialogs)
- **Extra Large**: `RoundedCornerShape(32.dp)` (Action docks, navigation sheets, bottom bars)
- **Pill**: `CircleShape` (Floating controls, search bars, zoom docks)

### B. Color Palette (`Color.kt` & `Theme.kt`)
- **Primary**: `TwitterBlue` (`#1D9BF0`) with high-chroma secondary (`#00A3FF`) and celestial cyan tertiary (`#38BDF8`).
- **Dark Chromatic Surfaces**:
  - `surface`: `Color(0xFF0B1118)` (Midnight Cosmic Slate)
  - `surfaceContainerLowest`: `Color(0xFF060A0F)`
  - `surfaceContainerLow`: `Color(0xFF0F1722)`
  - `surfaceContainer`: `Color(0xFF131E2C)`
  - `surfaceContainerHigh`: `Color(0xFF1A283A)`
  - `surfaceContainerHighest`: `Color(0xFF22344A)`
- **Light Chromatic Surfaces**:
  - `surfaceContainerLowest`: `Color(0xFFFFFFFF)`
  - `surfaceContainerLow`: `Color(0xFFF0F7FE)`
  - `surfaceContainer`: `Color(0xFFE5F1FC)`
  - `surfaceContainerHigh`: `Color(0xFFD8E9F9)`
  - `surfaceContainerHighest`: `Color(0xFFCBE0F5)`

### C. Motion Engine (`ExpressiveMotion.kt`)
- `Modifier.expressiveBounce()`: Responsive touch micro-interaction that subtly scales down to `0.97f` on press and springs back with `Spring.DampingRatioMediumBouncy` on release.
- Smooth spring animations on tab switching, sheet transitions, and floating pill docks.

### D. Typography (`Type.kt`)
- Prominent `FontWeight.ExtraBold` display titles with tighter tracking (`-0.5.sp`).
- High-contrast hierarchy between bold headline metadata and legible monospace arXiv identifiers.

---

## 3. Component Transformations Across Screens

1. **Feed & Cards (`PaperCard.kt`, `FeedScreen.kt`)**:
   - Cards redesigned with **26dp smooth expressive corners**, subtle chromatic border stroke, and `expressiveBounce` tap feedback.
   - Category chips and citation counts elevated into expressive pill badges.
   - QuickFilterTabs upgraded to expressive segmented pill shapes with spring slide indicators.

2. **Primary Action Dock & Reader (`PrimaryActionDock.kt`)**:
   - Transformed into a **floating 32dp pill dock** with tonal elevation, spring-loaded buttons, and integrated M3 Expressive dual-mode progress indicator.

3. **Personal Library (`LibraryScreen.kt`)**:
   - `ExpandedLibraryListItem` transformed to 26dp expressive containers with swipe-to-dismiss background feedback.
   - Docked linear progress indicator under the top app bar rendered with extra-rounded capsule ends.

4. **PDF Viewer (`PdfViewerScreen.kt`)**:
   - Floating zoom dock and page scrubber styled with expressive pill shapes (`CircleShape`) and spring-responsive action buttons.

5. **Search & Explore (`ExploreScreen.kt`)**:
   - Search bar styled as a 30dp expressive pill with dynamic icon morphing and spring-loaded suggestion chips.

---

## 4. Architecture & Technical Flow

```
                      ┌────────────────────────────┐
                      │    AstroXploreTheme.kt     │
                      └─────────────┬──────────────┘
                                    │
           ┌────────────────────────┼────────────────────────┐
           ▼                        ▼                        ▼
 ┌───────────────────┐    ┌───────────────────┐    ┌───────────────────┐
 │ Expressive Shapes │    │ Expressive Colors │    │ Expressive Motion │
 │ (24dp - 32dp)     │    │ (Twitter Blue)    │    │ (Spring Physics)  │
 └─────────┬─────────┘    └─────────┬─────────┘    └─────────┬─────────┘
           │                        │                        │
           └────────────────────────┼────────────────────────┘
                                    │
       ┌────────────────────────────┴────────────────────────────┐
       ▼                            ▼                            ▼
┌──────────────┐             ┌──────────────┐             ┌──────────────┐
│  Feed & Card │             │  Action Dock │             │  PDF Viewer  │
│ Components   │             │ & Library    │             │  & Search    │
└──────────────┘             └──────────────┘             └──────────────┘
```

### Planned File Edits
1. **`app/src/main/java/com/example/astroxplore/ui/theme/Shapes.kt`**:
   - Define M3 Expressive shapes (`ExpressiveShapes`) with 8dp, 12dp, 18dp, 26dp, and 32dp corner scales.
2. **`app/src/main/java/com/example/astroxplore/ui/theme/Color.kt` & `Theme.kt`**:
   - Update dark & light color schemes with expressive cosmic surface containers and Twitter Blue chroma. Wire `ExpressiveShapes` to `MaterialTheme`.
3. **`app/src/main/java/com/example/astroxplore/ui/theme/Type.kt`**:
   - Update typography scale with punchy expressive display and headline weights.
4. **`app/src/main/java/com/example/astroxplore/core/ui/animation/ExpressiveMotion.kt`**:
   - Implement `Modifier.expressiveBounce()` and spring physics utilities.
5. **`app/src/main/java/com/example/astroxplore/features/feed/ui/components/PaperCard.kt`**:
   - Update card styling to 26dp expressive containers with `expressiveBounce`.
6. **`app/src/main/java/com/example/astroxplore/core/ui/components/PrimaryActionDock.kt`**:
   - Upgrade action dock to 32dp floating container with expressive button springs.
7. **`app/src/main/java/com/example/astroxplore/features/library/ui/LibraryScreen.kt` & `FeedScreen.kt`**:
   - Polish filter chips and cards to expressive pill contours and spring transitions.
