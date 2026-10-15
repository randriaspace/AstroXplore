# Implementation Plan - Fix Group Creation & Supabase Sync

## 1. Prioritize Supabase RPC & Proper Error Propagation
- Update `createGroup` in `GroupRepository` to call `create_group` RPC first, then update meeting schedule/location.
- Propagate exceptions on server failure instead of swallowing them, ensuring the user gets clear feedback if creation fails.

## 2. Protect Unsynced Local Groups during Sync
- Update `syncGroups` to check `isSynced` flag (`isLocallyCreatedUnsynced`), preventing locally created groups from being prematurely pruned before network push.
