# Todo: Share Button in FullScreenPlayer

## Task 1: Add Share Button to FullScreenPlayer
**Description:** Add a Share button to the action row below the album art card in `FullScreenPlayer.kt`. Tapping it creates an Android `ACTION_SEND` intent with `createChooser`, sharing the YouTube URL for the current track (`https://www.youtube.com/watch?v=...` for remote tracks, or YouTube search query for local files).

**Acceptance criteria:**
- [x] Share button with `Icons.Default.Share` appears in the actions row below the album cover
- [x] Tapping opens the standard Android share sheet (showing Messenger, Instagram, WhatsApp, etc.)
- [x] The shared text is the YouTube URL for the track
- [x] Drag-down collapse and lyrics toggle on the cover remain completely unaffected

**Verification:**
- [x] Build: `./gradlew.bat assembleDebug`
- [ ] Test on device: tap Share on a streamed song, verify system chooser appears with YouTube URL

**Dependencies:** None
**Files:** `feature/player/src/main/java/com/mousikify/feature/player/FullScreenPlayer.kt`
**Scope:** S

---

## Checkpoint: Complete
- [x] `assembleDebug` passes
- [ ] Tested on device
