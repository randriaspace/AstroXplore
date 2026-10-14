# Implementation Plan - Onboarding & Profile Editing UI/UX Polish

## 1. Remove Feed Onboarding Banner
- Remove the setup preference banner from the feed screen.
- Ensure onboarding status (`isOnboarded`) is handled via settings/welcome wizard rather than polluting the main feed.

## 2. Dedicated Full-Screen Onboarding Wizard
- Create a dedicated, polished full-screen Onboarding Activity / Composable flow for first-time launch.
- Cover research preferences, key interests (Exoplanets, JWST, Cosmology, etc.), and affiliation setup.

## 3. Polished Edit Profile Screen UI/UX
- Redesign the Edit Profile screen with Material 3 immersive design:
  - Curated avatar selector & hero banner preview.
  - Enhanced research bio, institution affiliation, and academic title fields.
  - Interactive research interest chips/tags.
  - Clean floating action / save controls with smooth state transitions.
