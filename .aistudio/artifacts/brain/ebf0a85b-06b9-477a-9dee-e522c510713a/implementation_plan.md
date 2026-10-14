# Supabase Online Database Update & Synchronization Architecture Plan

A comprehensive architectural blueprint explaining how online Supabase configurations are updated, how the Android client gracefully adapts to missing online schemas without failing, and how all features and user roles are verified through automated testing and dual offline-to-online sync queues.

---

## User Review & Confirmed Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed based on your answers:
> - **Online Schema Update Method**: Single, 100% idempotent SQL migration script (`supabase/repair_and_sync_all.sql`) to run in your Supabase SQL Editor.
> - **App Fault Tolerance**: Client-side graceful fallback to Room local storage and direct table operations if an online table, column, or RPC is missing.
> - **Feature Coverage**: All core domains—Groups, Voting, Sessions, Profile, and Library.
> - **Role Permissions Testing**: Automated Robolectric and unit test suite covering Owner, Admin, Moderator, Member, and Applicant roles.
> - **Sync & Mutation Validation**: Dual validation covering both offline outbox queue replay and live database sync.

---

## 1. Overview & Core Concept

### Can the Agent Edit / Update Your Online Supabase Database Directly?

In cloud databases like Supabase:
1. **Client Security Boundary**: The Android app connects using `SUPABASE_ANON_KEY`. By Postgres security standards, client anon tokens are constrained by Row-Level Security (RLS) to CRUD data operations (`SELECT`, `INSERT`, `UPDATE`, `DELETE`). They intentionally cannot execute DDL commands like `CREATE TABLE`, `ALTER TABLE`, or `CREATE FUNCTION`.
2. **The 1-Step Solution**: We provide a **Single, Unified, 100% Idempotent SQL Migration** (`supabase/repair_and_sync_all.sql`).
   - You open your **Supabase Dashboard** -> **SQL Editor** -> Paste the script -> Click **"Run"**.
   - It safely creates missing tables, adds missing columns with `ADD COLUMN IF NOT EXISTS`, sets up foreign keys, configures RLS security rules, and deploys all stored procedures without touching existing data.

### Self-Healing Client Architecture

If an online table or column is temporarily missing or misconfigured, the Android application does not crash. It leverages a three-tier defensive strategy:

```
User Action (e.g. Vote, Create Club, Bookmark Paper, Add Review)
   │
   ▼
┌─────────────────────────────────────────────────────────────┐
│ Tier 1: Local Room Database (Immediate optimistic write)    │
│         Stored with isSynced = false                        │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ Tier 2: Attempt Remote RPC / Supabase Operation             │
└──────────────────────────────┬──────────────────────────────┘
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
         [ Success ]                   [ Error Encountered ]
               │                               │
               ▼                               ▼
       isSynced = true         ┌───────────────────────────────┐
                               │ Check Error Type:             │
                               │ - Missing RPC (PGRST202/404)  │
                               │ - Missing Column/Table (42P01)│
                               │ - Network Disconnected        │
                               └───────────────┬───────────────┘
                                               │
                                               ▼
                               ┌───────────────────────────────┐
                               │ Tier 3: Direct Table Fallback │
                               │ Or Retain in Outbox Queue     │
                               │ (Synced automatically later)  │
                               └───────────────────────────────┘
```

---

## 2. User Experience & Visual Design

The UI will provide continuous clarity and transparency around sync status and role capabilities:

- **Sync Status Pill & Badges**:
  - Unsynced or queued mutations display an unobtrusive cloud indicator (`CloudQueue` / `SyncProblem` icon) on items.
  - Tapping the status pill triggers an instant manual retry of `syncPendingMutations()`.
  - When the online database is updated and connected, the pill smoothly transitions to a green checkmark and auto-dismisses.
- **Role-Aware UI Affordances**:
  - **Admin / Owner**: Sees management controls (Edit Club, Approve/Decline Join Requests pill with pending counter, Role Promotion, Member Removal).
  - **Moderator**: Access to Session scheduling and review moderation without destructive club deletion rights.
  - **Member**: Consensus voting sliders/chips, Paper nomination button, RSVP attendance toggles, and peer review submissions.
  - **Applicant**: High-visibility "Application Pending" status banner on the hero header with clear instructions.
- **Zero-Crash Visual Safety**:
  - Missing online fields default smoothly to local fallbacks, preventing blank screens or error dialog popups during active usage.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Single Idempotent Migration vs Multiple Incremental Scripts**
  - *Chosen Approach*: One comprehensive file (`supabase/repair_and_sync_all.sql`) containing all tables, columns, indexes, and RPCs with `IF NOT EXISTS` guards.
  - *Why*: Eliminates ordering bugs and version drift. The user can run it at any point to bring any Supabase instance into 100% parity.
- **Decision 2: Optimistic Local-First Persistence with Room Outbox**
  - *Chosen Approach*: All user mutations are written immediately to Room with `isSynced = false` before calling the network.
  - *Why*: Guarantees instant sub-16ms UI responsiveness regardless of latency, and ensures zero data loss if network drops or online schema is missing.
- **Decision 3: RPC-to-Table Graceful Degradation**
  - *Chosen Approach*: Repositories catch Postgrest RPC error codes (`PGRST202`, `404`) and attempt direct table `insert` or `update` with client-generated UUIDs.
  - *Why*: Keeps features functional even if the user hasn't yet deployed the latest RPC stored procedures.

---

## 4. Technical Architecture & Data Strategy

### System Architecture & Data Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Android Client                                 │
│                                                                             │
│  UI Layer (Compose M3)                                                      │
│    ├── Groups Screen (Hero, Segmented Tabs, Quick Action Dock)             │
│    ├── Consensus Voting Screen (Vote Sliders, Glowing Badges)               │
│    ├── Sessions & Presentations Screen (Timeline, RSVP, Peer Reviews)       │
│    ├── Profile & Preferences Screen (Affiliation, Keywords, Bibstem)        │
│    └── Library Screen (Saved Papers, Citation Metrics, Offline Outbox)      │
│                                                                             │
│  Domain & Role Engine                                                       │
│    └── GroupRolePermissions (Owner, Admin, Moderator, Member, Applicant)    │
│                                                                             │
│  Data Layer (Repositories with Resilient Sync)                              │
│    ├── GroupRepository                                                      │
│    ├── VotingRepository                                                     │
│    ├── SessionRepository                                                    │
│    ├── LibraryRepository                                                    │
│    └── ProfileRepository                                                    │
│                                                                             │
│  Local Persistence (Room SQLite)                                            │
│    ├── AppDatabase (groups, group_members, group_papers, group_votes, etc.) │
│    └── Outbox Tables (pending_paper_deletions, pending_mutations)           │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ HTTPS / WSS
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Online Supabase Backend                           │
│                                                                             │
│  Public Tables (with Row-Level Security Policies)                           │
│    ├── profiles, keywords, user_preferences                                │
│    ├── groups, group_members, group_join_requests                           │
│    ├── group_papers, group_paper_votes                                      │
│    ├── group_presentations, group_session_reviews                           │
│    └── saved_papers                                                         │
│                                                                             │
│  Stored Procedures (RPCs)                                                   │
│    ├── create_group                                                         │
│    ├── join_group_by_code                                                   │
│    ├── approve_join_request                                                 │
│    └── check_in_session                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Role-Based Access Control (RBAC) Matrix

| Feature Action | Owner | Admin | Moderator | Member | Applicant |
|---|---|---|---|---|---|
| Browse Public Preprints & Library | Yes | Yes | Yes | Yes | Yes |
| Apply to Join Club with Code | N/A | N/A | N/A | N/A | Yes |
| Cast Consensus Votes | Yes | Yes | Yes | Yes | No |
| Nominate Preprints to Shelf | Yes | Yes | Yes | Yes | No |
| Schedule Presentations & RSVP | Yes | Yes | Yes | Yes | No |
| Submit Session Reviews | Yes | Yes | Yes | Yes | No |
| Edit Club Metadata & Schedule | Yes | Yes | No | No | No |
| Approve / Reject Join Requests | Yes | Yes | No | No | No |
| Change Member Roles | Yes | Yes | No | No | No |
| Kick Members | Yes | Yes | No | No | No |
| Archive / Dissolve Club | Yes | No | No | No | No |

---

## 5. Verification Plan & Test Suite

### Automated Robolectric & Unit Tests (`app/src/test/java/com/example/astroxplore/features/sync/`)

1. **`GroupRolePermissionsTest.kt`**:
   - Asserts permission evaluations across all roles (Owner, Admin, Moderator, Member, Applicant).
   - Validates that unauthorized mutations throw designated domain exceptions before network requests are dispatched.
2. **`SchemaFallbackAndOutboxTest.kt`**:
   - Simulates `PGRST202` (missing RPC) response and verifies direct table fallback execution.
   - Tests missing column tolerance in Kotlin serialization.
   - Simulates offline queuing and verifies replay of all pending records when connectivity is restored.
3. **`LibrarySyncAndDeletionTest.kt`**:
   - Verifies bookmarking papers offline, queueing deletions in `pending_paper_deletions`, and syncing cleanly on reconnection.
4. **`DualQueueSyncValidationTest.kt`**:
   - Tests concurrent local mutations and server-side state merges to ensure idempotency.

---

## 6. Execution Steps (Post-Approval)

1. **Generate SQL Script**: Write `supabase/repair_and_sync_all.sql` with full schema, RLS policies, and RPC definitions.
2. **Enhance Repository Resilience**: Implement RPC-to-table degradation and missing-column safety in repositories.
3. **Add Test Suite**: Create the unit and Robolectric tests in `app/src/test/`.
4. **Compile & Verify**: Run `gradle :app:testDebugUnitTest` and `compile_applet` to ensure pristine build health.
