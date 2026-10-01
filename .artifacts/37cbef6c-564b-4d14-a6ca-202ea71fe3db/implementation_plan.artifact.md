# Implementation Plan - Feed Screen Refresh & Header Visibility

## Goal
Improve the Feed screen experience by:
1. Making the Feed screen feel fresh when opened (always show the header with welcoming message, notification bar, and search bar)
2. Implementing a "quick refresh" experience with skeleton loading indicators
3. Ensuring the UI remains responsive and polished

## User Review Required

> [!IMPORTANT]
> **User Feedback**: The user wants the Feed screen to feel fresh when opened, with the header (Welcome message, notification bar, search bar) always visible at the top. They also want a smooth loading experience that doesn't feel like the screen is scrolling or jumping around.

## Proposed Changes

### 1. Header Visibility Fix
- **Problem**: The header sometimes disappears when scrolling or loading.
- **Solution**: Implement a more robust scroll detection and header visibility logic.
- **Implementation**: Modify `FeedScreen.kt` to ensure the header is always visible when the screen first loads, and only hides when the user scrolls down.

### 2. Offline-First Feed with Skeleton Loading
- **Problem**: Users see a blank or loading state when offline.
- **Solution**: Implement a skeleton loader that shows when offline, and only shows actual content when online or cached data is available.

### 3. UI/UX Improvements
- **Smooth Transitions**: Ensure all UI elements animate smoothly.
- **Consistent Spacing**: Maintain consistent padding and spacing throughout the feed.
- **Clear Feedback**: Show loading skeletons during refreshes and loading states.

### Detailed Task Breakdown

### Phase 1: Database & Network Setup (Already Completed)
- ✅ Database schema created (Groups, Group Papers, etc.)
- ✅ Network connectivity monitoring implemented (NetworkConnectivityObserver)
- **Pending**: Ensure feed data is cached locally for offline use

### Phase 2: UI/UX Implementation (Current Phase)

#### 2.1 Feed Screen Header Visibility
- **Problem**: Header disappears when scrolling or loading
- **Solution**: Implement a scroll-position-based header visibility check
- **Files to modify**: `FeedScreen.kt`

#### 2.2. Feed Content Loading
- **Problem**: Feed content loads slowly, causing perceived lag
- **Solution**: Implement skeleton loading states that appear when data is loading
- **Files to modify**: `FeedViewModel.kt`, `FeedScreen.kt`

#### 2.3. Feed Loading Logic
- **Problem**: Feed content loads too quickly or inconsistently
- **Solution**: Add explicit loading state management with skeleton placeholders
- **Files to modify**: `FeedViewModel.kt`, `FeedScreen.kt`

#### 5. UI Components
- **FeedScreen.kt**: Update to handle the new loading states and ensure header visibility
- **GroupPickerSheet.kt**: Ensure it works with the new feed structure
- **PaperCard.kt**: Ensure clickable areas are properly defined for the new header layout

### Implementation Plan

1. **Update FeedViewModel.kt**: Add state for loading more content and ensure proper state management
2. **Update FeedScreen.kt**:
   - Fix scroll-to-top behavior on initial load
   - Ensure header remains visible when appropriate
   - Add skeleton loading states for better UX
3. **UI Components**
   - Create a new `OfflineFeedState` composable for the "you're offline" message
   - Ensure the "Go to Library" CTA is prominent

### Implementation Steps

1.  **Update FeedViewModel.kt**:
   - Add `isLoadingMore` state
   - Implement `loadNextPage()` function
   - Ensure proper state management for feed items

2. **Update FeedScreen.kt**:
   - Ensure proper scroll-to-top behavior on entry
   - Add skeleton loading indicators
   - Ensure header visibility logic is robust

3. **UI Components**
   - Update `GroupPickerSheet.kt` to handle new feed structure
   - Verify all Composable functions are up to date

4. **Verification**
   - Run build to ensure no regressions
   - Test on multiple device configurations
   - Verify header visibility on first load

### Implementation Plan

1.  **Update FeedViewModel.kt**:
   - Add `isLoadingMore` state
   - Implement `loadNextPage()` function
   - Ensure proper state management for feed data

2.  **Update FeedScreen.kt**:
   - Fix scroll-to-top behavior
   - Add skeleton loading indicators
   - Ensure header stays visible during refreshes

3.  **UI Components**
   - Create `OfflineFeedState` composable
   - Update `Scaffold` layout to accommodate new content
   - Ensure all UI states are properly handled

### Implementation Details:

1.  **Feed ViewModel Enhancements**:
   - Add `_isLoadingMore` state
   - Implement `refresh()` and `loadNextPage()` methods with proper error handling
   - Ensure `feedPapers` StateFlow correctly combines local cache and remote data

2.  **FeedScreen.kt Updates**:
   - Add `isLoadingMore` state
   - Implement `animateScrollToItem` for smooth scrolling
   - Add `OfflineFeedState` composable for offline feedback
   - Ensure header visibility logic is robust

Let's proceed with the implementation.

<tool_call>
<function=set_plan_state>
<parameter=state>
REFINING