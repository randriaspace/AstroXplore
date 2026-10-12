# Academic Research Search & Gemini AI Research Briefing Plan

Enhance AstroXplore for astronomers and astrophysics researchers by removing the annoying loading bar under the feed filters, overhauling the Search/Explore screen into an academic-grade literature search console, and introducing Gemini AI-powered **Trending & Suggested Research Briefings** that synthesize user interests, active journal club discussions, and top astronomical citation patterns with one-tap NASA ADS filters.

---

## 1. Remove Annoying Loading Indicator Under Feed Filter Tabs
- In `FeedScreen.kt`:
  - Locate and remove `AstroM3DockedLinearProgress(visible = isRefreshing)` directly underneath `QuickFilterTabs(...)`.
  - The feed screen already has the pull-to-refresh swipe indicator and the full-page cosmic constellation loader for empty queries; removing the docked line completely eliminates the visual clutter under the tabs.

---

## 2. Elevate Explore/Search Screen for Professional Astronomers
Astronomers and astrophysics researchers need high-signal literature discovery rather than casual browsing. We will augment the Explore experience with:

### A. Academic Syntax & ADS Query Power Assist
- Add quick-insert syntax chips in the search bar assistance bar:
  - `bibcode:`, `author:^FirstAuthor`, `abs:"phrase"`, `year:2024-2026`, `property:refereed`, `database:astronomy`.
- Provide instant regex detection and badge indicators when the researcher types standard astronomical identifiers (e.g. arXiv identifier `2403.12345`, ADS Bibcode `2024ApJ...900...12V`, or ORCID `0000-0002-1825-0097`).

### B. High-Precision Academic Filters
- Expand the fast filter carousel with:
  - Top astronomical bibstems: `ApJ` (Astrophysical Journal), `MNRAS` (Monthly Notices of the RAS), `A&A` (Astronomy & Astrophysics), `AJ` (Astronomical Journal).
  - Open Access (`property:openaccess`), Catalog Data (`property:data`), and Software Code (`property:software`).

---

## 3. Gemini AI Research Briefing & Smart Synthesis
Astronomers in research groups and journal clubs want personalized, scientifically grounded literature suggestions.

### A. AI Research Synthesis Engine (`AiResearchRecommender.kt`)
- Call Gemini `gemini-3.5-flash` via clean REST service using Retrofit / Serialization.
- Provide a resilient fallback synthesizer when offline or without an API key (using local Room keyword clusters and journal club presentation titles).
- Synthesizes 4 rich contextual sources:
  1. **User Research Interests & Keywords**: Cosmological parameters, exoplanet atmospheres, high-energy astrophysics, etc.
  2. **Active Journal Club Topics**: Recent papers and discussion presentation titles from joined clubs.
  3. **Saved Papers Library**: Highly cited papers saved locally by the user.
  4. **High-Impact Citation Benchmarks**: Current landmark papers in ApJ, MNRAS, and Nature Astronomy.

### B. Dedicated AI Research Briefing Cards (`AiResearchBriefingCard.kt`)
On the Explore screen (idle state):
- Replace static generic banners with an **"AI Research Briefing"** section:
  - **"Why Recommended" badge** (e.g. *"Synthesized from your Journal Club 'JWST Exoplanet Club' & saved papers"*).
  - **Academic Hypothesis / Overview**: A crisp 2-sentence rationale on why this specific research cluster is trending now.
  - **Recommended Query Pill**: Formatted ADS query button (e.g., `(JWST OR "transmission spectroscopy") AND property:refereed AND year:2025-2026`).
  - **One-Tap Explore Action**: Tapping immediately launches the exact ADS query with all appropriate filters pre-applied.

---

## 4. Verification & Testing Plan
1. **Compilation Check**: Run `compile_applet` to verify all Kotlin files, serializers, and layouts build without warnings or errors.
2. **Automated Unit Tests**: Run `gradle :app:testDebugUnitTest` to verify Room DAOs, ViewModels, and search query builder tests pass.
3. **UI Verification**:
   - Verify that pulling down to refresh the feed no longer renders the docked blue progress line under the filter chips.
   - Verify the Explore screen renders the new Academic Search console and Gemini AI Research Briefing cards.
   - Verify that tapping an AI research suggestion populates the query and executes the NASA ADS search.
