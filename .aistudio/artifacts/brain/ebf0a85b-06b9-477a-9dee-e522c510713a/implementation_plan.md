# Offline Launch Resilience & Immersive Cosmic Feed Loading Plan

Resolve the first-launch offline screen blockage with a 2-second timeout and offline library fallback, and replace the generic feed skeletons with an expressive **Cosmic Orbital Constellation Loader** featuring pulsating celestial glow, dynamic star-field orbital rings, and astrophysics survey telemetry.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your selected preferences:
> - **Offline Launch Blockage Resolution**: Implement a **2-second timeout gate** in `MainViewModel` and `MainActivity`. When launching completely offline or when Supabase session initialization takes longer than 2000ms, the app unblocks automatically, treats the user as an offline guest or loads cached credentials, and directs to the app feed/Saved Papers Library with a distinct offline indicator instead of freezing on the splash screen.
> - **Immersive Feed Loading**: Upgrade the feed loading state from plain gray card skeletons to a **Cosmic Orbital Constellation Loader** with:
>   - Concentric orbital rings with pulsating dashed strokes.
>   - Interconnected celestial constellation nodes pulsing with harmonic alpha glows.
>   - Deep cosmic radial glow gradient anchored by Twitter Blue (`#1D9BF0`) and Celestial Cyan (`#38BDF8`).
>   - Animated telescope telemetry captions ("Scanning cosmological surveys...", "Aligning radio interferometer...", "Querying arXiv astrophysics preprints...").
>   - Expressive 26dp cosmic card silhouettes with smooth celestial shimmer sweep.

---

## 1. Problem Analysis & Root Cause

### Why the app was blocked on the loading screen offline:
1. `MainActivity.kt` used:
   ```kotlin
   splashScreen.setKeepOnScreenCondition { sessionStatus == SessionStatus.Initializing }
   val isReady = sessionStatus !is SessionStatus.Initializing && ...
   ```
2. When launching without internet connectivity, Supabase Auth client's `sessionStatus` flow remains in `SessionStatus.Initializing` indefinitely while waiting for DNS resolution or server response.
3. Because `sessionStatus` never transitions away from `Initializing`, `startDestination` remains `null`, the system splash screen stays locked, and the Compose splash overlay is never dismissed.
4. **Fix**: Introduce a definitive 2-second timeout (`withTimeoutOrNull(2000L)`) combined with network awareness (`networkConnectivityObserver`). If offline or if Supabase session initialization does not complete within 2 seconds, resolve the session to an offline guest state, load local preferences, dismiss the splash, and open the app with full offline library access.

---

## 2. Proposed Changes & Architecture

### A. First-Launch Offline Timeout (`MainViewModel.kt` & `MainActivity.kt`)
- In `MainViewModel`:
  - Add `val isInitTimeout = MutableStateFlow(false)`.
  - Launch a 2000ms timeout job on startup. If `sessionStatus` is still `Initializing` after 2000ms:
    - Set `_isInitTimeout.value = true`.
    - Check local Room profile / DataStore onboarding status. If locally onboarded or offline, resolve destination immediately to `Screen.MainGraph` (or `Screen.AuthGraph` if explicitly logged out).
- In `MainActivity.kt`:
  - Update `setKeepOnScreenCondition { sessionStatus == SessionStatus.Initializing && !isInitTimeout }`.
  - Update `isReady` calculation so that reaching the 2-second timeout immediately produces a valid `startDestination`.
  - Gracefully fade out the splash overlay into the main UI.

### B. Immersive Cosmic Orbital Constellation Loader (`CosmicConstellationLoader.kt`)
- Create a dedicated component: `CosmicConstellationFeedLoader.kt`:
  - **Custom Canvas Constellation**:
    - Animated rotation angle using `rememberInfiniteTransition()`.
    - Concentric orbital paths (semi-major/semi-minor ellipses) with soft celestial glow.
    - Pulsating star nodes connected by constellation vector chords with animated dash phases.
    - Pulsar core with radiant radial gradient expanding and contracting organically.
  - **Rotating Astrophysics Telemetry**:
    - Animated status text switching smoothly between survey states:
      - *"Calibrating cosmological survey..."*
      - *"Connecting to NASA ADS & arXiv mirrors..."*
      - *"Mapping astronomical keywords & citations..."*
  - **Expressive Shimmer Silhouettes**:
    - Staggered 26dp expressive containers with cosmic slate surface colors (`#131E2C` / `#1A283A`) and subtle cyan border sweeps.

### C. Feed Screen Integration (`FeedScreen.kt`)
- Replace the 5 standard gray `PaperCardSkeleton()` placeholders in `FeedScreen.kt` with `CosmicConstellationFeedLoader`.
- Ensure pull-to-refresh and filter switching display the celestial loader with smooth enter/exit animations (`fadeIn` + `expandVertically`).

---

## 3. Verification Plan

1. **Compilation**: Run `compile_applet` to ensure zero compilation or type errors.
2. **Unit Tests**: Run `gradle :app:testDebugUnitTest` to verify all existing Robolectric and unit tests pass.
3. **Offline First-Launch Validation**:
   - Verify that simulated offline connectivity or delayed auth response triggers the 2-second timeout fallback and releases the splash screen into the main app.
   - Verify that the feed displays the offline banner and allows navigating to the Saved Papers Library.
4. **Visual Polish**:
   - Verify smooth 60fps canvas animations on the constellation loader.
   - Verify dark and light theme colors on the cosmic loader.
