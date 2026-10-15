# Implementation Plan - Online-Only Group CRUD Architecture

Transition journal club group management (Create, Read/Sync, Update, Dissolve/Delete, Join/Leave) to an online-only architecture backed directly by Supabase, preventing local cache desynchronization, phantom creations, and accidental pruning.

## User Review Required

> [!IMPORTANT]
> As requested, all Group management operations will strictly require an active internet connection and an authenticated Supabase user session. When offline, users will be informed via clear UI messaging that group creation, editing, and dissolution require internet access. Cached groups will remain readable offline in read-only mode.

- **Offline Mode Behavior**: Existing groups remain viewable locally in read-only mode, but modifying or creating groups will require active connectivity to eliminate conflict scenarios.
- **Immediate Supabase Validation**: Creation and updates will wait for confirmation from Supabase before notifying success or updating the local cache.

---

## Proposed Changes

### Data Layer: `GroupRepository.kt`

- **Enforce Online Requirement**: Add explicit connectivity and active Supabase session validation to `createGroup`, `updateGroup`, `dissolveGroup`, and `joinGroup`.
- **Robust Group Creation**:
  - Invoke Supabase `create_group` RPC / direct postgrest insert with complete error propagation (no silent failures or false success returns).
  - On Supabase success, immediately persist the newly created group record and owner membership to Room cache.
- **Fix Group Synchronization & Pruning Bug**:
  - Replace aggressive deletion logic in `syncGroups()` that previously deleted local records if Supabase fetch or membership checks encountered timing delays.
  - Ensure local Room cache accurately mirrors Supabase state.
- **Reliable Update & Dissolve**:
  - `updateGroup`: Update remote Supabase `groups` row (name, description, focus_area, schedule, location); update Room cache on success.
  - `dissolveGroup`: Call Supabase deletion/archive; purge local Room records upon Supabase confirmation.

### Presentation Layer: `CollaborationViewModel.kt` & Group Dialogs / Screens

- **Accurate Operation Feedback**:
  - Catch network and Supabase exceptions and display actionable error messages in the UI snackbar/banner (e.g., "Internet connection required to create a group", "Failed to create group on server: [reason]").
  - Prevent dismissal of creation/edit dialogs when remote operations fail.
  - Disable or show an offline banner on group creation/edit buttons when disconnected.

---

## Verification Plan

### Automated Tests
- Run Gradle unit tests if present via `compile_applet` and test commands.
- Verify Kotlin compiler builds without errors.

### Manual Verification
1. **Online Group Creation**:
   - Create a new group while connected to the internet.
   - Verify the group is created on Supabase and appears in the list.
   - Pull-to-refresh or restart app: verify the group remains present and does not disappear.
2. **Online Group Editing & Dissolution**:
   - Update group name, description, and meeting schedule; verify changes persist on Supabase and in app.
   - Dissolve/delete group; verify removal from Supabase and the UI.
3. **Offline Guard**:
   - Disconnect network and attempt to create or edit a group.
   - Verify an explicit notification informs the user that an internet connection is required, preventing inconsistent local state.
