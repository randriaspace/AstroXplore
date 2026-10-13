# Implementation Plan: World-Class Material 3 Expressive Group Details & Flow

Elevate the Journal Club **Group Details** screen and collaboration flows in AstroXplore into an academic-grade, world-class experience following **Material 3 Expressive** design principles, as aligned through clarifying requirements.

---

## 1. Expressive Hero Header & Live Session Banner
- **Cosmic Celestial Header (`GroupHeroHeader`)**:
  - Replace the generic app bar and flat sub-banner with an immersive hero container utilizing celestial radial gradient accents, subtle starry glow (`Brush.radialGradient`), and deep M3 surface styling.
  - Display Journal Club Name, astronomical focus area chip (e.g. *Exoplanet Atmospheres*, *Cosmic Dawn*), member count badge, and owner attribution.
  - **Live Session & Calendar Sync Status Pill**:
    - High-visibility pill indicating meeting schedule (e.g., *"Thursdays 15:00 UTC • Room 402 / Zoom"*).
    - One-tap **"Add to Google Calendar"** sync with instant feedback.
  - **Quick Action Header Icons**:
    - QR Code & Club Invite sheet trigger with copyable clean code.
    - Club settings/edit modal trigger (for leaders & admins).
    - Share link / code intent chooser.

---

## 2. Expressive Segmented Pill Bar Navigation
- **Navigation Modernization**:
  - Replace generic `PrimaryScrollableTabRow` with an expressive **Segmented Pill Navigation Bar**:
    - 5 Core Sections: **Shelf** (curated preprints), **Voting** (consensus prioritization), **Sessions** (talks & recaps), **Analytics** (club KPIs & metrics), and **Admin** (membership & governance).
    - Tonal container with smooth animated pill background for the active tab.
    - Badged indicators (e.g., active vote count on *Voting*, pending join requests on *Admin*).

---

## 3. Quick Action Dock
- **World-Class Collaborative Floating / Pinned Dock**:
  - Provide a high-ergonomics quick action dock for members:
    - **Nominate Paper**: Fast bottom sheet to add preprints from NASA ADS / arXiv bibcode or title.
    - **Vote on Agenda**: Jump directly to consensus voting.
    - **RSVP / Session Check-in**: One-tap attendance and check-in confirmation.
  - Integrated with organic spring physics (`expressiveBounce`) on press and release.

---

## 4. Enhanced Interactive Consensus Voting (`ConsensusVoting.kt`)
- **Visual & Micro-Interaction Polish**:
  - Expressive voting cards with 24.dp rounded corners and dynamic color accents.
  - Glowing journal badge tags (e.g., `ApJ`, `MNRAS`, `A&A`, `arXiv`).
  - **Live Animated Consensus Progress Bar**: Visualizes the proportion of club members that voted for each paper with smooth animated percentage fills.
  - **Tactile Voting Button**: Dynamic filled / tonal icon button with spring scale feedback on toggle.
  - **"Nominate for Discussion Talk"**: One-tap trigger pre-populating speaker scheduling.

---

## 5. Polished Sessions & Presentation Timeline (`SessionsTab`)
- **Presentation Timeline**:
  - Visual connected timeline displaying Upcoming Sessions vs Past Discussion Archives.
  - Presentation cards showing speaker, scheduled date/time, paper bibcode, meeting link, and discussion slides status.
  - **Interactive Peer Review Feedback Flow**:
    - Visual star rating summaries and peer review commentary chips.
    - Expressive bottom sheet for members to submit constructive session notes and discussion takeaways.
    - One-tap QR session check-in confirmation.

---

## 6. Club Analytics & KPIs Polish (`KpiTab`)
- **Visual Analytics Cards**:
  - Expressive metrics grid: Papers Read & Discussed, Attendance Rate %, Active Voting Turnout, and Top Bibstem Distribution.
  - Clean M3 elevated tonal cards with astronomy iconography.

---

## 7. Verification & Testing Plan
- **Compilation Check**: Run `compile_applet` to ensure all composables, animations, and dependencies compile without issues.
- **Unit & Robolectric Tests**: Run `gradle :app:testDebugUnitTest` to verify Group repository flows, voting actions, presentation scheduling, and UI state transitions pass.
- **Interactive Verification**: Verify navigation between Shelf, Voting, Sessions, and Analytics, calendar intent launching, voting state toggle, and invite dialog presentation.
