# Seamless Auth-to-Setup Navigation & Supabase Cloud Profile Sync

Resolve login-to-onboarding transitions by eliminating intermediate Feed screen flashes, fix Postgrest payload serialization so account setup and profile edits reliably persist to Supabase, and ensure returning onboarded users are never prompted with setup after logging out.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed based on your preferences:
> - **Seamless Readiness Gate**: The app maintains a polished branded splash overlay while verifying Supabase onboarding status upon authentication, eliminating screen flicker or intermediate feed flashes.
> - **Non-Intrusive Cloud Sync Feedback**: Profile changes and setup submissions will display subtle status snackbars with offline retry indicators rather than disruptive modal dialogs.

- **Confirmed Decision 1**: Hold the readiness barrier until `isOnboarded` is definitively resolved (`true` or `false`) so navigation directly mounts either `Screen.OnboardingGraph` or `Screen.MainGraph`.
- **Confirmed Decision 2**: Replace untyped Kotlin maps with JSON primitives and lenient serializers for Supabase Postgrest upserts to ensure cloud storage operations succeed without silent serialization failures.

---

### 1. Overview & Core Concept

- **What It Does**: 
  1. Ensures users logging in or confirming signups transition smoothly into either Account Setup (`OnboardingGraph`) or the main discovery feed (`MainGraph`) with zero visual glitching or screen flash.
  2. Guarantees that all academic details (organization type, institution name, country, education level, ORCID) and keyword preferences are immediately and reliably posted to the Supabase `profiles` and `user_preferences` cloud tables.
  3. Ensures that once a user completes account setup, logging out and logging back in correctly detects their onboarded state from Supabase so the setup wizard never reappears.
- **Target Audience / Persona**: Researchers, students, and astrophysics scholars who require seamless authentication and persistent profile data across multiple devices and sessions.
- **Key Value**: Rock-solid cloud data synchronization, eliminating data loss and frustrating repeated onboarding prompts.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  - **New User Login / Setup**:
    1. User enters credentials on `LoginScreen` or finishes signup.
    2. Authentication succeeds; app displays seamless splash/loading indicator while querying Supabase for profile status.
    3. Profile indicates incomplete setup (`is_onboarded = false`) -> App navigates directly into Account Setup (`Screen.OnboardingGraph`) with zero flash of the Feed.
    4. User enters academic affiliation, education level, country, and research tags, then taps "Continue".
    5. Data is committed to Room and Supabase in real time; user sees the welcome celebration screen and transitions to the Feed.
  - **Returning User Login**:
    1. User logs in.
    2. Supabase profile query returns `is_onboarded = true`.
    3. App routes directly to `Screen.MainGraph` (Feed) with zero intermediate screens.
  - **Profile Editing & Sync**:
    1. User edits fields in `EditProfileScreen`.
    2. Debounced auto-save and explicit "Save Changes" post changes directly to Supabase via `JsonObject` payload with RLS-compliant user email.
    3. Subtle snackbar confirms "Profile synchronized to cloud", with automatic retry if offline.

- **Visual Identity & Theme**:
  - Maintains existing deep astrophysics dark mode (`Surface #0D1117`, `PrimaryContainer #1F2937`, `Electric Accent #38BDF8`).
  - Polished Material 3 transition animations with zero jitter between authentication, setup, and main feed.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Strict Readiness Barrier in `MainActivity`**:
  - *Chosen Approach*: `isReady` requires `sessionStatus !is SessionStatus.Authenticated || isOnboarded != null`.
  - *Why*: Previously, `isReady` became true immediately upon authentication while `isOnboarded` was still `null`. Because `isOnboarded == false` evaluated to false, `startDestination` prematurely defaulted to `MainGraph` (the Feed), creating a visible flash before redirecting to setup.
  - *Alternatives Considered*: Navigating after mounting. (Rejected: always causes visible flicker between routes).

- **Decision 2: Supabase Postgrest Serialization Fix**:
  - *Chosen Approach*: Use `buildJsonObject` with explicit JSON primitives (`put("id", ...)`, `put("email", ...)`, `put("is_onboarded", true)`) and configure `Postgrest` client serializer with `ignoreUnknownKeys = true` and `coerceInputValues = true`.
  - *Why*: Passing `Map<String, Any>` to Supabase-kt's `postgrest.upsert()` causes `SerializationException: Serializer for class 'Any' is not found`. This was caught silently in `ProfileRepository`, causing remote updates to fail silently and leaving the Supabase database with unonboarded records.
  - *Alternatives Considered*: Custom polymorphic serializer. (Rejected: `buildJsonObject` is built directly into `kotlinx.serialization.json` and natively supported by Supabase-kt).

- **Decision 3: RLS Policy Compliance for Profile Updates**:
  - *Chosen Approach*: Explicitly populate `email` from `authRepository.currentUser?.email` in all update payloads.
  - *Why*: Postgres RLS policies on `public.profiles` require `email = (auth.jwt() ->> 'email')`. Any payload missing or having a mismatching email is rejected by Supabase RLS.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌────────────────────────────────────────────────────────┐
│                   Authentication Flow                   │
│                                                        │
│  [ Login / Signup ]                                    │
│          │                                             │
│          ▼                                             │
│  [ SessionStatus.Authenticated ]                       │
│          │                                             │
│          ▼ (Hold Splash Screen / Readiness Gate)       │
│  [ Query Supabase profiles table via ProfileRepository ]│
│          │                                             │
│    ┌─────┴──────────────────┐                          │
│    ▼                        ▼                          │
│ [ isOnboarded == false ]  [ isOnboarded == true ]      │
│    │                        │                          │
│    ▼                        ▼                          │
│ [ Screen.OnboardingGraph ] [ Screen.MainGraph (Feed) ] │
│   (Zero Feed Flash)                                    │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│               Profile Synchronization Flow             │
│                                                        │
│  [ Onboarding Submit / Edit Profile Save ]             │
│          │                                             │
│          ▼                                             │
│  [ Construct JSON Payload (buildJsonObject) ]          │
│    - id: auth.uid()                                    │
│    - email: auth.email                                 │
│    - affiliation_type, education_level, country        │
│    - is_onboarded: true                                │
│          │                                             │
│    ┌─────┴────────────────────────────────┐            │
│    ▼                                      ▼            │
│ [ Local Room Cache ]              [ Supabase Cloud DB ]│
│   profiles table                    public.profiles    │
│   (Immediate UI update)             (Upsert via Postgrest)
│                                           │            │
│                                           ▼            │
│                                   [ Cloud Sync Success]│
└────────────────────────────────────────────────────────┘
```

#### State & Component Mapping:
1. `MainActivity.kt`:
   - Update `isReady` calculation to keep splash visible while authenticated session evaluates onboarding state (`isOnboarded != null`).
   - Eliminate redundant navigation redirects between `AuthGraph` and `MainGraph`.
2. `core/network/NetworkModule.kt`:
   - Configure Supabase client JSON serialization with `ignoreUnknownKeys = true` to gracefully handle Postgres `TIMESTAMPTZ` columns (`updated_at`, `created_at`).
3. `features/profile/data/ProfileRepository.kt`:
   - Replace untyped `Map<String, Any>` in `updateProfile()` and `getProfile()` with valid `JsonObject` payloads.
   - Enforce authenticated user email in the payload to satisfy Supabase RLS.
   - Re-throw / surface Supabase exceptions so sync state is accurately reported.
4. `features/onboarding/ui/OnboardingViewModel.kt`:
   - Await successful remote update in `completeOnboarding` before advancing to welcome screen.
   - Ensure `_isOnboarded.value = true` is updated in `MainViewModel`.
5. `features/profile/ui/EditProfileViewModel.kt`:
   - Provide clear sync feedback via snackbar state on successful cloud write.
