# Implementation Plan - Fixing Profile Auto-Provisioning & Job Cancellation

## Problem Statement
1. New users created in Supabase `auth.users` lack a corresponding row in `public.profiles`, causing profile loading failures and blocking access to the profile screen.
2. Coroutine cancellations (`JobCancellationException`) are caught by broad try-catch / error mappers, logging technical errors (`ErrorMapper`) and disrupting navigation and screen state.

## Proposed Changes

### 1. Error Handling (`ErrorMapper.kt`)
- Check if `e` is an instance of `kotlinx.coroutines.CancellationException` (or `JobCancellationException`). If so, rethrow or return without logging `Log.e`.

### 2. Profile Repository (`ProfileRepository.kt`)
- In `getProfile(userId)` and `getUserProfileDto(userId)`, if the remote profile row is `null` (missing from `public.profiles`), automatically provision/upsert a default profile record in `public.profiles` using the authenticated user's email and metadata.
- Ensure fallback to local profile and local preferences is robust and does not swallow cancellation exceptions.

### 3. Profile ViewModel (`ProfileViewModel.kt`)
- Ensure profile loading catches exceptions gracefully without propagating `JobCancellationException` to the UI or throwing unhandled errors when navigating to the profile screen.

## Verification Plan
- Compile applet with `compile_applet`.
- Run unit tests with `gradle :app:testDebugUnitTest`.
