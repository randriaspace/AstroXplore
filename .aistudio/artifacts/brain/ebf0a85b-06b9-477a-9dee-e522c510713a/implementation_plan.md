# Implementation Plan - Bulletproof Onboarding & Polished Profile UI/UX

Fix the root cause where newly registered users with missing profile records or unpopulated professional fields (`affiliation_type`, `country`, `education_level` being NULL) bypass the Onboarding screen, and redesign both the Onboarding and Edit Profile screens with polished Material 3 multi-step cards, smooth progress animations, quick-pick dropdown sheets, and avatar headers.

---

## 1. Root Cause Analysis: Why Onboarding Was Skipped

1. **Database Default / Trigger Behavior**:
   - In Supabase, when a new account registers, a trigger or default insert creates a `profiles` row with `is_onboarded = TRUE` (as seen in the user's Supabase screenshot), even though `affiliation_type`, `country`, and `education_level` are `NULL`.
2. **Flawed `isOnboarded` Evaluation**:
   - `MainViewModel.kt` and `LoginViewModel.kt` previously trusted `profile.isOnboarded == true` or checked if *any* local record existed. Since `is_onboarded` was true in the database row, the app evaluated the user as onboarded and booted them directly to the Feed, leaving professional details empty.
3. **Solution**:
   - A user is **truly onboarded** if and only if `profile.isOnboarded == true` **AND** their core professional details (`affiliationType`, `country`, `educationLevel`) are **non-blank/non-null**. If any of these required fields are missing, `isOnboarded` must evaluate to `false`, forcing the user into the Onboarding flow.

---

## 2. Planned Changes & Enhancements

### A. Bulletproof Onboarding Gate (`MainViewModel.kt`, `LoginViewModel.kt`, `AuthRepository.kt`)
- Update `isOnboarded` check in `MainViewModel` and `LoginViewModel` to strictly require:
  `profile.isOnboarded == true && !profile.affiliationType.isNullOrBlank() && !profile.country.isNullOrBlank() && !profile.educationLevel.isNullOrBlank()`
- Ensure newly built default profiles explicitly initialize `isOnboarded = false`.

### B. Revamped Onboarding Screen UI/UX (`OnboardingScreen.kt`)
- **Multi-Step Card Flow with Illustrations**:
  - Step 1: Professional Background (Affiliation Type, Institution/Name, Country, Education Level) with clean M3 outlined text fields and quick-pick dropdown sheets.
  - Step 2: Research Interests (Full-screen tag selector with search, live counter `3-6 topics selected`, and pinned selected chips).
- **Smooth Progress & Polish**: Animated linear progress bar, subtle tonal elevation cards, 48dp+ touch targets, and a top-bar "Skip" button.

### C. Revamped Edit Profile Screen UI/UX (`EditProfileScreen.kt`)
- **Grouped Cards & Avatar Header**:
  - Top hero profile card with dynamic scholar initials avatar, editable name, and affiliation summary.
  - Grouped M3 section cards for Personal Information, Academic Affiliation, and Research Credentials (ORCID ID).
  - Quick-pick bottom sheets for Affiliation Type, Country, and Education Level.
  - Floating or bottom "Save Changes" action with ripple feedback and success feedback.

---

## 3. Verification Plan

### Automated Build Verification
- Run `compile_applet` to confirm zero compilation errors.
- Run `gradle :app:testDebugUnitTest` to verify test suite health.

### Manual Verification Scenarios
1. **New Account Registration**: Create a fresh account or simulate a Supabase profile with `NULL` professional fields; verify the app immediately presents the Onboarding screen instead of bypassing it.
2. **Onboarding Completion**: Fill in professional details and select 3–6 interests; verify smooth transition to the main app and correct database persistence (`is_onboarded = true`).
3. **Edit Profile UI**: Navigate to Profile -> Edit Profile; verify the redesigned grouped card layout, avatar header, and interactive dropdown pickers work flawlessly.
