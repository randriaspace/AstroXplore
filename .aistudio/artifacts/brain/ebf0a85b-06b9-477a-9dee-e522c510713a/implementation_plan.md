# Google-Class Polish & Modular Paper Details Reader

Elevate AstroXplore to a world-class Google-grade reading and research application by redesigning the Paper Details experience into an elegant, clean reader with interactive tabs and collapsible metadata, while extracting a comprehensive atomic design component suite for reuse across the entire app.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following directions were confirmed during interactive clarification and will govern the implementation upon your approval:

- **Confirmed Polish Priority**: Paper Details experience overhauled with a prominent hero header, fluid tabbed navigation (Overview, Citations & Metrics, BibTeX/Export), and quick bottom action dock.
- **Confirmed Component Strategy**: Dedicated shared UI package (`core.ui.components` and `core.ui.widgets`) housing atomic, reusable building blocks to eliminate duplicated code and keep files maintainable under 500 lines.
- **Confirmed Reader Layout**: Clean academic reader typography with collapsible metadata sections (authors list expander, journal details accordion, and interactive citation badges).

---

### 1. Overview & Core Concept

- **What It Does**: Transforms scientific paper browsing from dense text walls into a polished, distraction-free reading sanctuary inspired by Google Scholar and Google Play Books. Users can fluidly toggle between an executive summary, metrics/citations, and metadata, interact with copy/share/group utilities, and launch local offline PDFs or web preprints.
- **Target Audience / Persona**: Astrophysicists, astronomy students, and space science enthusiasts who need to rapidly assess papers, examine citations, share to journal reading clubs, and read preprints without friction or visual clutter.
- **Key Value**: Delivers instantaneous clarity through clean hierarchy, eliminates long monolithic source files by breaking views into testable reusable widgets, and guarantees standard 60fps edge-to-edge responsiveness.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Paper Discovery to Deep Dive**: Tapping any paper card in the feed, search, or library performs an edge-to-edge transition into the Paper Details screen.
2. **Hero Overview & Quick Navigation**: The top bar displays clean back navigation, favorite/bookmark toggle, and share action. Right below, a streamlined publication card highlights the date, category badge, paper title (with full LaTeX formula rendering), and expandable author list.
3. **Tabbed Content Navigation**:
   - **Overview Tab**: Key highlights, citation badges, collapsible full abstract with smooth animated expansion, and journal club discussions.
   - **Citations & Metrics Tab**: Citation count analytics, bibcode/arXiv references, external ADS links, and citation graph context.
   - **BibTeX & Export Tab**: Formatted code block with single-tap clipboard copy, customizable export formats, and share sheet triggers.
4. **Docked Primary Action Bar**: A floating, elevated bottom bar offering persistent 1-tap "Read PDF" (with automatic download / offline cached opening) and "Add to Journal Club" actions.

#### Visual Identity & Theme
- **Aesthetic Direction**: Academic clarity combined with Google Material 3 precision. Utilitarian elegance with generous breathing room, high contrast labels, and crisp typography.
- **Color Palette & Mood**: 
  - Accent: Twitter Blue (`#1D9BF0`) / Deep Cosmic Blue (`#0B57D0`) as primary action colors.
  - Surfaces: M3 dynamic container tiers (`surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`) ensuring clean visual separation without hard borders.
  - Badges & Chips: Subtle semi-transparent tonal tints for arXiv and category identifiers.
- **Typography & Hierarchy**:
  - Paper Titles: `titleLarge` / `headlineSmall` (bold, line height 28–32sp) with sub/superscript formatting.
  - Section Headers: `labelLarge` uppercase tracking (1.2sp letter spacing) in primary color.
  - Body / Abstract: `bodyLarge` (line height 24sp) with comfortable contrast for long reading sessions.
- **Component Styling & Layout**:
  - 16dp and 20dp rounded corners following modern M3 shapes.
  - Edge-to-edge insets handled via `safeDrawing` and `navigationBarsPadding`.

#### Interactive Feedback & Motion
- Smooth tab switching with `AnimatedContent` crossfade.
- Spring animations (`spring(stiffness = Spring.StiffnessMediumLow)`) for expanding/collapsing author chips and abstract text.
- Micro-haptic tactile feedback on copy, bookmark, and swipe actions.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Tabbed Reader vs Single Continuous Scroll**
  - *Chosen Approach*: Primary content organized into 3 focused tabs (`Overview`, `Metrics`, `BibTeX`) with a sticky hero header.
  - *Why*: Reduces cognitive load for dense 20-page astrophysics publications; users who only need BibTeX or citation metrics don't need to scroll past 6 paragraphs of abstract.
  - *Alternatives Considered*: Infinite vertical scrolling was previously used, but created excessive page length and buried export actions.

- **Decision 2: Centralized Atomic Component Library**
  - *Chosen Approach*: Introduce reusable widgets in `core.ui.components` and `core.ui.widgets` (`AstroTabRow`, `CollapsibleSection`, `MetadataBadgeRow`, `ActionDock`, `AuthorChipsFlow`).
  - *Why*: Eliminates boilerplate across `FeedScreen`, `SearchScreen`, `LibraryScreen`, and `PaperDetailsScreen`, keeping individual screen files under 350 lines.
  - *Alternatives Considered*: Inlining components per screen led to code duplication and visual inconsistencies in buttons and badges.

- **Decision 3: Seamless Offline PDF Integration**
  - *Chosen Approach*: The primary "Read PDF" button inspects local Room cache (`SavedPaperEntity.localFilePath`) first; if cached, opens instantly via internal viewer or FileProvider; if not, triggers background streaming with linear progress.
  - *Why*: Delivers an offline-first, instant-load experience matching world-class document readers.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        AstroXplore UI Layer                            │
├────────────────────────────────────────────────────────────────────────┤
│  PaperDetailsScreen                                                    │
│  ├── AstroTopAppBar (Navigation, Bookmark, Share)                      │
│  ├── PublicationHeroHeader (Title, ArXiv Tag, Authors Expander)        │
│  ├── AstroTabRow (Overview | Metrics | BibTeX)                         │
│  │   ├── TabOverview: Collapsible Abstract + Category Chips           │
│  │   ├── TabMetrics: Citation Card, Bibcode, ADS Reference Link       │
│  │   └── TabBibTeX: Syntax Formatted Card + 1-Tap Copy Action          │
│  └── PersistentBottomDock (Read PDF / Download | Journal Club)         │
└────────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     Shared Reusable Component Suite                     │
├────────────────────────────────────────────────────────────────────────┤
│  • AstroTabRow.kt              - M3 animated pill/indicator tab row    │
│  • CollapsibleSection.kt       - Expandable card with spring animation │
│  • MetadataBadgeGroup.kt       - Uniform citation/arXiv/category chips │
│  • AuthorChipsFlow.kt          - Expandable author list with avatars   │
│  • PrimaryActionDock.kt        - Standardized bottom floating dock     │
│  • BibTeXCodeBlock.kt          - Styled monospaced export card         │
└────────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    ViewModel & Repository Services                     │
├────────────────────────────────────────────────────────────────────────┤
│  PaperDetailsViewModel                                                 │
│  ├── SavedPaperDao (Room DB, DownloadState Flow, Citation counts)      │
│  ├── PdfDownloadManager (WorkManager / Streaming Cache)                │
│  └── JournalGroupRepository (Journal clubs & group discussions)        │
└────────────────────────────────────────────────────────────────────────┘
```

#### Data Model & State Mapping
- `PaperDetailsUiState`: Sealed state hierarchy (`Loading`, `Success(paper, isSaved, downloadState)`, `Error(message)`).
- `selectedTab`: Integer state (0 = Overview, 1 = Metrics, 2 = BibTeX) driving `AnimatedContent`.
- `isAuthorsExpanded`: Boolean state governing whether to truncate to first 3 authors or display the complete collaboration list.
- `downloadState`: Bound to `DownloadState` (`NOT_DOWNLOADED`, `DOWNLOADING(progress)`, `DOWNLOADED`, `FAILED`).

#### Step-by-Step Implementation Strategy
1. **Create Shared Reusable Component Suite**:
   - `core/ui/components/AstroTabRow.kt`: Clean M3 pill tab selector.
   - `core/ui/components/CollapsibleSection.kt`: Header with arrow toggle and animated content reveal.
   - `core/ui/components/AuthorChipsFlow.kt`: Elegant author monograms with expandable modal/drawer.
   - `core/ui/components/BibTeXCodeBlock.kt`: Syntax-highlighted monospaced container with copy button.
   - `core/ui/components/PrimaryActionDock.kt`: Reusable bottom dual-action bar.
2. **Refactor & Modularize PaperDetailsScreen**:
   - Split monolithic `PaperDetailsScreen.kt` into the main screen shell and focused sub-composables.
   - Incorporate the tabbed layout, collapsible abstract, and rich metric cards.
   - Wire the PDF download and reader routing cleanly with download state progress.
3. **Verify App Build**:
   - Compile and verify with `compile_applet`.
