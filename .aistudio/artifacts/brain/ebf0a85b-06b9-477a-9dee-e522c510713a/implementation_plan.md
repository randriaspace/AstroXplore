# Implementation Plan - Journal Clubs UI Polish & Progressive Creation Wizard

Transform the Journal Clubs experience with a polished multi-step creation wizard, curated astrophysics visual identities (icons and color themes), and enriched club cards featuring meeting countdowns, activity metrics, and sleek academic aesthetics.

---

## User Preferences & Clarifications
* **Creation Flow**: Multi-step sheet with clear progressive steps (Identity -> Schedule -> Venue & Review).
* **Club Card Details**: Meeting countdown badge, accent theme colors, and activity stats (members, shelf papers, votes).
* **Visual Identity**: Curated astrophysics icons (Telescope, Galaxy, Orbit, Satellite, Atom, Supernova) and color palettes.

---

## Proposed Changes

### 1. Multi-Step Group Creation Wizard (`GroupsScreen.kt`)
* Refactor `CreateClubSheet` into a progressive 3-step wizard with a stepper indicator:
  * **Step 1: Identity & Theme**:
    - Club Name and optional description.
    - Curated space icon selector (Telescope, Galaxy, Orbit, Satellite, Pulsar, Atom).
    - Curated accent color palette selector (Nebula Purple, Deep Space Cyan, Supernova Amber, Cosmic Emerald, Solar Rose).
    - Focus area quick chips (JWST Galaxies, Exoplanets, Gravitational Waves, etc.) and custom input.
  * **Step 2: Meeting Cadence & Schedule**:
    - Frequency selector: `Weekly` vs `Monthly`.
    - Weekly: interactive 7-day multi-select chips (`Mon` to `Sun`).
    - Monthly: Weekday ordinal (`1st`, `2nd`, `3rd`, `4th`, `Last`) + Day of week OR calendar date presets (`1st`, `15th`, `Last day`).
    - Time selector (presets + custom input).
    - Live formatted schedule preview card.
  * **Step 3: Venue & Review**:
    - Meeting venue or virtual link (e.g., Google Meet link or Seminar Hall).
    - Summary card showing icon, theme, formatted cadence, venue, and private club code preview.
  * **Navigation**: Step indicators (`1. Identity`, `2. Cadence`, `3. Venue`), "Back" and "Next / Create" buttons with smooth animated transitions.

### 2. Enhanced Journal Club Cards (`GroupsScreen.kt`)
* Redesign `ClubCard`:
  * **Themed Avatar**: Prominent icon container using the club's theme color and icon.
  * **Meeting Status & Cadence Badge**: High-contrast pill badge highlighting the schedule (e.g. *"Every Thu at 4:00 PM"* or *"1st Friday"*).
  * **Activity Stats Row**:
    - Member count chip (`N members`).
    - Paper shelf count chip (`N papers nominated`).
    - Focus topic pill.
  * **Copy Club Code Badge**: Monospace code tag with quick copy action and visual feedback.
  * **Card Surface Polish**: Subtle accent container gradient/stroke conforming to Material Design 3 guidelines.

### 3. Header & Empty State Polish (`GroupsScreen.kt`)
* **Header Banner**: Display active research community stats, next upcoming session indicator, and quick "Join by Code" and "Scan QR" actions.
* **Empty State**: Inviting academic illustration/icon with direct actions to start a club or enter a colleague's invite code.

---

## Verification Plan

### Compilation & Build Verification
* Run `compile_applet` to verify clean compilation with zero warnings or errors.

### Manual Verification Scenarios
1. **Multi-Step Creation Wizard**:
   - Open Journal Clubs -> tap "Start a Club".
   - Step 1: Pick an icon (e.g. Telescope), accent color (e.g. Nebula Purple), and enter a club name.
   - Step 2: Choose Weekly -> select Tue and Thu -> pick 4:00 PM. Verify live schedule preview.
   - Step 3: Enter venue link -> verify summary card -> tap "Create Journal Club".
2. **Club Card Visuals**:
   - Verify newly created club renders with chosen icon, accent color, meeting badge, activity stats, and unique code.
   - Tap "Copy Code" to verify clipboard copy and confirmation toast.
3. **Monthly Cadence Support**:
   - Create a second club with Monthly frequency (e.g. 1st Friday) and verify the card badge correctly displays the cadence.
