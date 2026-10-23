# Journal Club Calendar Sync & Meeting Cadence

Polish the Journal Club meeting scheduling experience by introducing structured date, time, and recurrence frequency synchronization (Weekly, Bi-weekly, Monthly) that integrates directly with Android device calendars (Google Calendar, Samsung Calendar, Outlook) and exports universal RFC-5545 `.ics` iCalendar invites compatible with iOS Apple Calendar and macOS.

### User Review & Critical Decisions

> [!IMPORTANT]
> - **Universal Calendar Sync**: Implements both a 1-tap **Android Calendar Intent** (with pre-filled title, discussion agenda, paper bibcode, and `RRULE` recurrence) and an **RFC-5545 `.ics` File Export** to guarantee cross-platform compatibility for members on iOS, macOS, or desktop.
> - **Recurring Cadence**: Supports **Weekly**, **Bi-weekly**, and **Monthly** intervals, plus one-off seminar talks, with custom day-of-week and time picker selections.
> - **Rich Academic Agenda**: Calendar event descriptions automatically embed the scheduled paper bibcode, paper title, NASA ADS link, AstroXplore deep link, meeting location/video link, and discussion talking points.

---

### 1. Overview & Core Concept

- **Goal**: Enable journal clubs to schedule discussions, seminars, and regular club sessions with precise date, time, and recurrence, and allow all club members to sync sessions to their personal calendar with a single tap.
- **Target Audience**: Astronomy students, academic researchers, and journal club organizers coordinating reading groups across distributed universities and observatories.
- **Key Value**: Eliminates manual meeting entry and missed journal club sessions by keeping academic schedules in sync across Android and future iOS devices.

---

### 2. User Experience & Visual Design

- **Journal Club Hero Header Sync Card**:
  - Displays the next meeting session with live countdown/pill (*"Every Thursday at 17:00 UTC"*).
  - Tapping **"Sync"** opens the **Calendar Sync Sheet**:
    - **Add to Google Calendar / Device**: Opens system calendar with title, location, recurrence rule (`RRULE:FREQ=...`), and formatted agenda pre-populated.
    - **Export & Share .ics Invite**: Generates an RFC-5545 `.ics` file and invokes the Android share sheet for sharing to colleagues on iOS (Apple Calendar), Outlook, or email lists.
- **Enhanced Schedule Presentation Sheet**:
  - When nominating or scheduling a paper discussion, organizers select:
    - **Session Date & Time**: Modern Material 3 DatePicker and TimePicker dialogs.
    - **Frequency Chips**: *One-off*, *Weekly*, *Bi-weekly*, *Monthly*.
    - **Meeting Venue / Link**: Pre-filled with club's default Google Meet / Zoom link or room.
    - **Sync to My Calendar**: Toggle checkbox to immediately add to the presenter's calendar upon confirmation.
- **Visual Design (Material 3 Expressive)**:
  - High-contrast meeting badges (*"Next Session: Thu, Oct 29 • 17:00"*).
  - Clean modal bottom sheets with 48dp minimum touch targets.
  - Tonal action buttons with calendar and share icons.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dual Android Intent + Universal `.ics` RFC-5545 Export**:
  - *Chosen Approach*: Support both direct `Intent.ACTION_INSERT` for Android device calendars and generated `.ics` files for cross-platform sharing (iOS iCal, macOS, Outlook).
  - *Why*: Zero runtime calendar permissions needed (no invasive `READ_CALENDAR`/`WRITE_CALENDAR` prompts), zero Google account API friction, and 100% interoperability with iOS for future cross-platform club members.
  - *Alternatives Considered*: Direct CalendarProvider writes with runtime permissions (invasive and requires dangerous permissions; rejected).
- **Decision 2: Structured Recurrence Parsing & Formatting**:
  - *Chosen Approach*: Store structured cadence in `meeting_schedule` (e.g., `Weekly on Thursdays at 17:00`) with helper parser that extracts `DayOfWeek`, `LocalTime`, and `RRULE` strings.
  - *Why*: Backward-compatible with existing clubs in the database while giving members clean, automated calendar recurrence rules.

---

### 4. Technical Architecture & Component Flow *(Technical Reference)*

```
┌─────────────────────────────────────────────────────────────┐
│                   Journal Club Meeting Sync                  │
│                                                             │
│   [ GroupHeroHeader / Presentation Card ]                   │
│          │                                                  │
│          ▼ (onSyncCalendarClick)                            │
│   ┌──────────────────────────────────────────────┐          │
│   │           CalendarSyncBottomSheet            │          │
│   │  - Next Session Date & Time                  │          │
│   │  - Cadence (Weekly, Bi-weekly, Monthly)      │          │
│   │  - Agenda / Paper preview                    │          │
│   │                                              │          │
│   │  [ Button: Add to Device Calendar ]          │          │
│   │    └── Android Intent (CalendarContract)    │          │
│   │        with RRULE & begin/end time           │          │
│   │                                              │          │
│   │  [ Button: Export .ics Invite (iOS / Mac) ]  │          │
│   │    └── CalendarIcsGenerator                  │          │
│   │        via FileProvider (text/calendar)      │          │
│   └──────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

#### Detailed Changes Planned:
1. **`core/util/CalendarSyncUtils.kt`**:
   - `buildCalendarInsertIntent(group, presentation)`: Constructs `Intent(Intent.ACTION_INSERT)` with title, location, description, begin/end millis, and `RRULE` (`FREQ=WEEKLY;INTERVAL=1`, etc.).
   - `generateIcsContent(group, presentation)`: Generates valid RFC-5545 `.ics` payload with `VEVENT`, `RRULE`, `UID`, `SUMMARY`, `DESCRIPTION`, `LOCATION`, and `URL`.
   - `shareIcsFile(context, group, presentation)`: Writes `.ics` to cache directory and launches `ACTION_SEND` with FileProvider Uri.
2. **`res/xml/file_paths.xml`**:
   - Add `<cache-path name="calendar" path="calendar/" />` to enable secure FileProvider sharing of `.ics` calendar invites.
3. **`features/groups/ui/components/CalendarSyncBottomSheet.kt`**:
   - Material 3 bottom sheet showing meeting details, recurrence badge, paper agenda, and action buttons for "Add to Device Calendar" and "Share .ics Invite".
4. **`features/groups/ui/GroupDetailsScreen.kt`**:
   - Replace the simplistic `syncGroupToCalendar` with the new interactive `CalendarSyncBottomSheet`.
   - Update `SchedulePresentationSheet` with time selection, recurrence frequency chips, and 1-tap calendar sync.
5. **Verification**:
   - Verify build compilation (`compile_applet`) and run test suites (`gradle :app:testDebugUnitTest`).
