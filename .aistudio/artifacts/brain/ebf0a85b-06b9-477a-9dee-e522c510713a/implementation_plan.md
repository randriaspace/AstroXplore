# Implementation Plan - App Onboarding Tour, Post-Registration Academic Setup & Profile Sync Architecture

Restructure the onboarding and registration lifecycle into two distinct stages: a first-launch feature tour stored in local DataStore (replaying when app storage is cleared) and a post-auth academic profile setup flow. Additionally, overhaul profile editing with two-way Room Flow reactivity, auto-save, and instant Supabase synchronization so edited scholar information is never lost.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed by the user in Phase 1:
> - **Flow Separation**: Decoupled the visual **First-Launch Feature Tour** from the **Post-Registration Academic Setup**. Users see the interactive feature tour upon first launch; after email confirmation and login, new accounts complete their academic credentials and keywords.
> - **Onboarding Storage**: Stored the first-launch app tour completion flag in **Local DataStore** (`onboarding_complete`), ensuring that clearing app cache or storage re-triggers the tour as in standard mobile applications.
> - **Profile Sync & Persistence**: Adopted **auto-save on field change** with debounce, backed by an **explicit Save button**, direct Room database reactivity via Kotlin Flow, and background Supabase upserting to guarantee no fields revert to empty defaults.

---

## 1. Overview & Core Concept

- **Purpose**: Deliver an intuitive first-run mobile onboarding experience, seamless academic credential onboarding for newly verified scholars, and reliable, zero-data-loss profile editing.
- **Audience**: Astrophysics scholars, researchers, and students discovering research papers, organizing journal clubs, and maintaining academic credentials.
- **Key Deliverables**:
  1. **First-Launch App Tour (`AppTourScreen`)**: Multi-step interactive feature cards with smooth pager transitions showcasing AstroXplore's capabilities, stored in DataStore.
  2. **Academic Profile Setup (`AcademicSetupScreen`)**: Dedicated post-registration flow for new scholars to configure academic background, affiliations, and initial research keyword preferences.
  3. **Zero-Data-Loss Profile Sync**: Reactive Room Flow binding in `ProfileViewModel` and `EditProfileScreen` with debounced state synchronization and instant local-first persistence.

---

## 2. User Experience & Visual Design

### Key User Flows

```
┌────────────────────────────────────────────────────────────────────────┐
│ New User Journey                                                       │
│                                                                        │
│ 1. First Launch (DataStore: hasCompletedAppTour = false)               │
│    └─► App Feature Tour (World-class pager cards, Get Started / Login) │
│                                                                        │
│ 2. Authentication                                                      │
│    └─► Sign Up (Name, Email, Password) ──► Confirm Link ──► Log In     │
│                                                                        │
│ 3. Post-Auth Evaluation (Strict Online Supabase Check)                 │
│    ├─► Brand New Scholar (is_onboarded == false / missing fields)      │
│    │     └─► Academic Setup (Affiliation, Degree, Country, Keywords)   │
│    │           └─► Welcoming Animation ──► Main Feed                   │
│    │                                                                   │
│    └─► Existing Scholar (is_onboarded == true)                         │
│          └─► Direct Entry to Main Feed                                 │
└────────────────────────────────────────────────────────────────────────┘
```

### Visual Identity & Theme
- **App Tour**: Full-bleed edge-to-edge pager with Material 3 expressive surface tonal elevations, cosmic icon illustrations, page indicator dots, and vibrant primary action buttons.
- **Academic Setup**: Clean, structured Material 3 cards organized into semantic sections (Public Identity, Academic Affiliation, Research Focus, Research Credentials) matching the edit profile architecture.
- **Edit Profile**: Seamless auto-updating form fields using `remember(profile)` with optimistic local updates and immediate feedback indicators (e.g., "Saved to device • Syncing online").

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Local DataStore for App Tour vs. Supabase for Academic Setup**
  - *Chosen Approach*: The visual app tour is client-scoped (DataStore). If the user clears app storage or reinstalls, the tour greets them again. In contrast, academic credentials (`affiliation`, `education_level`, `keywords`) are stored in Supabase with Room caching.
  - *Why*: Separates device presentation state from durable user data.

- **Decision 2: Reactive Room Flow in Profile Screen & Edit Profile**
  - *Chosen Approach*: `ProfileViewModel` continuously collects `profileRepository.getLocalProfile(userId)` instead of a one-time DTO fetch on `init`.
  - *Why*: When the user edits their profile and taps Back or Save, the Room database update immediately triggers UI recomposition on `ProfileScreen` with zero latency or stale state.

- **Decision 3: Field State Keying in EditProfileScreen**
  - *Chosen Approach*: Replace static `remember { mutableStateOf("") }` with `remember(profile.id, profile.updatedAt)` and LaunchedEffects that bind the incoming Room/Supabase profile data directly to editable text inputs.
  - *Why*: Eliminates the bug where fallback defaults permanently locked input fields to empty values.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ MainActivity / Navigation Router                            │
│                                                             │
│ Check 1: DataStore (hasCompletedAppTour)                    │
│   ├─ false ──► Screen.AppTour                               │
│   └─ true  ──► Check 2: Supabase Session                    │
│                  ├─ NotAuthenticated ──► Screen.AuthGraph   │
│                  └─ Authenticated    ──► Check 3: Profile   │
│                                            ├─ Incomplete    │
│                                            │    └─► Setup   │
│                                            └─► MainGraph    │
└──────────────────────────────┬──────────────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
┌───────────────────────────────┐   ┌───────────────────────────┐
│ Room Database (Local)         │   │ Supabase (Remote)         │
│ - profiles table (Flow)       │◄──┼─► profiles table (Upsert) │
│ - user_preferences (Keywords) │   │ - user_preferences       │
│ - DataStore (App Tour State)  │   │ - auth.users              │
└───────────────────────────────┘   └───────────────────────────┘
```

### Component & State Mapping

| Component | Responsibility | State / Triggers |
| :--- | :--- | :--- |
| `AppTourScreen` | 3-step feature introduction (ArXiv, AI, Groups) | `hasCompletedAppTour` in DataStore; navigates to Auth |
| `AcademicSetupScreen` | Post-login wizard for affiliation, degree, keywords | Saves to Room & Supabase, sets `is_onboarded = true` |
| `EditProfileScreen` | Modifies researcher credentials | Keyed `remember(profile)` state, debounced auto-save & explicit Save button |
| `ProfileViewModel` | Real-time researcher profile display | Observes `getLocalProfile()` Flow; auto-refreshes on Room changes |
| `MainViewModel` | Global routing and onboarding state | Evaluates DataStore tour state + Supabase profile completion |

---

## 5. Verification & Test Plan

1. **Clear Storage Simulation**: Clear app data or toggle DataStore flag; verify that `AppTourScreen` appears on first launch.
2. **New User Flow**: Register a new user, verify email, log in; confirm that the user is routed to `AcademicSetupScreen` to configure academic credentials and keywords, followed by the welcoming banner and the Feed.
3. **Existing User Flow**: Log in with an already completed account; confirm immediate bypass directly to `Feed`.
4. **Profile Edit Persistence**: Navigate to Edit Profile, modify name, affiliation, and ORCID; verify that changes immediately reflect on `ProfileScreen` upon saving or navigating back without clearing fields.
5. **Compilation Check**: Run `compile_applet` to ensure complete Kotlin build success.
