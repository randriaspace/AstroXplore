# Implementation Plan - Private Journal Clubs & Advanced Meeting Scheduler

Transform the Journal Clubs experience into a focused, private academic community platform by removing public club discovery and adding an interactive multi-day, multi-time, and frequency meeting scheduler for group creation.

## User Requirements & Clarifications
1. **Remove Public Clubs**: Remove the "Explore Public" tab and public discovery. Journal clubs are private to their members.
2. **Unified Clubs Screen**: Replace the 2-tab layout with a single unified list of "My Clubs" featuring prominent "Start a Club" and "Join Club" actions.
3. **Private Access**: Members join existing clubs exclusively via unique Club Code (e.g., `ASTRO8X2`) or QR code scanner.
4. **Advanced Scheduling**: In the group creation sheet, provide interactive day chips (multi-select), meeting time selector/chips, and meeting frequency selection (Weekly, Bi-weekly, Monthly, Twice a week).

---

## Proposed Changes

### 1. Group Creation UI & Scheduling (`GroupsScreen.kt`)
* Remove `PrimaryTabRow` ("My Clubs" / "Explore Public") in `GroupsScreen`.
* Enhance `CreateGroupSheet`:
  - **Meeting Frequency**: Segmented chips or dropdown for `Weekly`, `Bi-weekly`, `Monthly`, `Twice a week`.
  - **Days of the Week**: Multi-select filter chips (`Mon`, `Tue`, `Wed`, `Thu`, `Fri`, `Sat`, `Sun`) allowing users to pick one or multiple days.
  - **Meeting Time**: Preset time selector chips (`9:00 AM`, `11:00 AM`, `2:00 PM`, `4:00 PM`, `6:00 PM`) plus custom time input dialog.
  - Automatically compose the combined schedule description (e.g., *"Weekly on Tue, Thu at 3:00 PM"*).
  - Explicitly set `visibility = "private"` and `is_public = false` for all newly created groups.
* Update top bar actions to include:
  - "Scan QR Code" action button.
  - "Join by Code" action button with modal dialog.
  - Prominent Floating Action Button (FAB) / quick button for "Start a Club".
* Redesign `EmptyClubsState` with informative onboarding for private research groups and quick action buttons to join or create.

### 2. ViewModel & State Management (`GroupsViewModel.kt`)
* Remove public clubs state (`exploreGroups`) to streamline UI reactivity to `myClubs`.
* Update `uiState` to reflect joined/owned clubs: `GroupsUiState.Success(clubs: List<GroupModel>)`.
* Update `createGroup` parameters to accept structured `frequency`, `days`, `times`, `location`, and generate human-readable schedule formatting.
* Ensure joining via `displayId` (code) or QR scan works smoothly for private groups by matching the unique short code and enrolling the user as a member.

### 3. Repository & Data Layer (`GroupRepository.kt`)
* Update `createGroup` to save the formatted schedule, `visibility = "private"`, `is_public = false`, and enroll the creator as Admin.
* Update `syncGroups` to focus on user-affiliated groups (where user is owner or member in `group_members`), ensuring synced clubs always appear in the user's club roster.
* Ensure `joinGroupByDisplayId` looks up the private group by its unique code and upserts a membership row into `group_members`.

---

## Verification Plan

### Compilation & Build Verification
* Run `compile_applet` to confirm clean compilation with no syntax, import, or Compose state errors.

### Manual Verification Scenarios
1. **Club Creation with Multi-Day/Time/Frequency**:
   - Open Journal Clubs -> tap "Start a Club".
   - Select multiple days (e.g. Tuesday and Thursday), select meeting time (e.g. 3:00 PM), and select frequency ("Weekly").
   - Confirm the created club displays *"Weekly on Tue, Thu at 3:00 PM"* in the club card.
2. **Unified Private Club List**:
   - Confirm the screen shows a single unified list with no "Explore Public" tab.
   - Confirm empty state shows clear instructions on creating or joining with a code.
3. **Join by Code & QR Code**:
   - Open "Join Club" dialog, enter an existing club's `displayId`, and verify immediate enrollment and appearance in My Clubs.
