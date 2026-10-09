# Modern Material 3 Loading System & Seamless Startup Plan

Modernize all loading states, progress indicators, and transitions across AstroXplore to adhere to official Material Design 3 guidelines (m3.material.io/components/loading-indicator), replace legacy Lottie dependencies with native Compose M3 animations, implement expressive skeleton shimmer placeholders, and eliminate the startup onboarding flash.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed based on your requirements:
> - **Loading Indicator Aesthetic**: Expressive M3 circular and linear indicators paired with layout-matching skeleton shimmer placeholders for content screens.
> - **Zero-Flash Startup Engine**: Splash screen is held securely until both authentication status and onboarding persistence fully resolve before mounting the navigation host.
> - **Background Operation Feedback**: Top app bar indeterminate linear progress indicators with subtle snackbars for syncing, pulling, and pushing data.
> - **Lottie Phase-Out**: Complete removal of Lottie loading assets in favor of pure, lightweight, theme-aware Jetpack Compose M3 animations.

---

## 1. Overview & Core Concept

- **What It Does**: Replaces heavy third-party Lottie animations across AstroXplore with official Material 3 progress indicators and native Compose shimmer skeletons. Stabilizes the app startup lifecycle so authenticated users immediately enter the main feed without intermediate onboarding flickers.
- **Target Audience / Persona**: Astrophysicists, researchers, and students who require fast, fluid, distraction-free navigation when browsing publications, exploring topics, and syncing offline papers.
- **Key Value**: 
  - Zero UI flicker on app cold start.
  - Consistent Google-grade Material 3 motion language and tactile progress feedback.
  - Faster render times and lower memory footprint by removing Lottie JSON decoders.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Cold Start & Session Settling**:
   - The user opens the app.
   - A branded Material 3 splash screen displays the AstroXplore identity with an elegant, modern M3 indicator.
   - Only when **both** session authentication (`SessionStatus`) and DataStore onboarding status (`isOnboarded`) are verified does the splash gracefully cross-fade into the target screen (`Feed` for onboarded users, `Onboarding` for new users, or `Auth` for unauthenticated sessions).
   - No flicker or intermediate navigation state is ever rendered.

2. **Content Feed & Paper Details Loading**:
   - When fetching recent arXiv papers or loading paper details, the screen displays a subtle **M3 Skeleton Shimmer Placeholder** matching the exact geometry of paper cards (header pill, LaTeX title bar, metadata badges, summary lines).
   - Once data arrives, content fades in smoothly with a standard M3 easing curve (250ms).

3. **Background Sync & Pull-to-Refresh Feedback**:
   - When pulling to refresh or downloading PDF archives in the background, a discrete indeterminate **M3 LinearProgressIndicator** animates along the top app bar without obscuring content.
   - Non-intrusive M3 snackbars communicate sync completion or offline status.

### Visual Identity & Theme
- **Color Palette**: Standard AstroXplore M3 semantic tokens (`MaterialTheme.colorScheme.primary`, `surfaceVariant`, `outlineVariant`).
- **Shimmer Gradients**: Dynamic brush sweeping from `surfaceVariant.copy(alpha = 0.4f)` to `surfaceVariant.copy(alpha = 0.9f)` using infinite transition easing.
- **Elevation & Radius**: M3 rounded corners (`ShapeDefaults.Medium` and `Large`) across all skeleton elements.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native Compose M3 Indicators vs. Lottie Animations**
  - *Chosen Approach*: Replace all Lottie loaders (`LottieLoadingView`) with custom, reusable M3 components (`AstroM3LoadingIndicator`, `AstroLinearProgressBar`, and `AstroShimmerCard`).
  - *Why*: Eliminates APK bloat, removes frame drops caused by JSON parsing on lower-end devices, adapts automatically to dynamic M3 color schemes and Dark Mode, and aligns with Google Material 3 guidelines.
  - *Alternatives Considered*: Updating Lottie files with modern vectors (rejected due to persistent dependencies and lack of native Compose M3 theme reactivity).

- **Decision 2: Splash Gate Navigation Synchronization**
  - *Chosen Approach*: In `MainActivity.kt`, gate `RootNavHost` instantiation and splash overlay dismissal on a strict boolean condition: `sessionStatus !is SessionStatus.Initializing && (sessionStatus is SessionStatus.NotAuthenticated || isOnboarded != null)`. Keep the splash screen opaque until the start destination is statically immutable.
  - *Why*: Prevents `NavHost` from temporarily defaulting to `Screen.OnboardingGraph` for 100–300ms while DataStore reads `isOnboarded` asynchronously.
  - *Alternatives Considered*: Redirecting from inside `OnboardingScreen` (rejected as it causes the exact flash the user reported).

---

## 4. Technical Architecture & Component Flow

```
┌────────────────────────────────────────────────────────┐
│                      Cold Start                        │
└──────────────────────────┬─────────────────────────────┘
                           │
             ┌─────────────▼─────────────┐
             │ Session & Onboarding Gate │
             │  (Holds M3 Splash State)  │
             └─────────────┬─────────────┘
                           │ Authenticated & Ready
            ┌──────────────┴──────────────┐
            ▼                             ▼
 ┌──────────────────────┐      ┌──────────────────────┐
 │  First-Time Visitor  │      │  Returning Scientist │
 │ (Screen.Onboarding)  │      │   (Screen.MainGraph) │
 └──────────────────────┘      └──────────┬───────────┘
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
       ┌──────────────────────┐                       ┌──────────────────────┐
       │   Screen Loading     │                       │ Background Sync/Push │
       │  (Skeleton Shimmer & │                       │  (Top LinearProgress │
       │  M3 Circular State)  │                       │    Indicator & Bar)  │
       └──────────────────────┘                       └──────────────────────┘
```

### Component Structure & Modular Plan

1. **`core/ui/components/AstroLoadingIndicators.kt`** (New Unified File):
   - `AstroM3LoadingIndicator`: Expressive Material 3 circular progress indicator with primary/secondary track styling, configurable sizes (`Small`, `Medium`, `Large`), and optional status label.
   - `AstroLinearProgressBar`: Clean indeterminate linear progress indicator placed directly beneath app top bars during network queries.
   - `AstroShimmerEffect`: Reusable `Modifier.astroShimmer()` using `rememberInfiniteTransition` to render smooth sweeps on card placeholders.
   - `AstroPaperCardSkeleton`: Structural skeleton mockup mirroring `ExpandedLibraryListItem` and `PaperCard`.
   - `AstroDetailsSkeleton`: Structural skeleton for paper metadata and abstract.

2. **`MainActivity.kt`**:
   - Refactor splash overlay and start destination gating logic to guarantee zero-flash transitions.
   - Replace the Lottie book loader on the splash screen with a sleek, branded AstroXplore M3 loader.

3. **Screen Modernization Across All Features**:
   - `PaperDetailsScreen.kt`: Switch from `LottieLoadingView` to `AstroDetailsSkeleton`.
   - `LibraryScreen.kt`: Switch from `LottieLoadingView` to `AstroPaperCardSkeleton` list and top linear sync progress.
   - `ExploreScreen.kt`: Switch from `LottieLoadingView` to skeleton list.
   - `GroupsScreen.kt` & `GroupDetailsScreen.kt`: Switch from `LottieLoadingView` to M3 indicators.
   - `PdfViewerScreen.kt`: Switch to M3 determinate/indeterminate circular progress indicator with download percentage.
   - `InterestsScreen.kt` & `EditProfileScreen.kt`: Switch to M3 loading indicators.
   - Deprecate/clean up `LottieLoadingView.kt` and remove obsolete raw Lottie resources.
