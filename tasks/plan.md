# Implementation Plan: Share Button in Full-Screen Player

## Overview
Add a Share button (`Icons.Default.Share`) to the full-screen song view (in the action bar alongside Favorite and Add to Playlist). When tapped, it launches the standard Android system share sheet (`Intent.ACTION_SEND` with `Intent.createChooser`) containing the song's YouTube URL (`https://www.youtube.com/watch?v=<videoId>`).

## Architecture Decisions
- **System Intent**: Use native Android `Intent.ACTION_SEND` with `text/plain` MIME type and `Intent.createChooser`. No third-party SDKs or social sharing libraries.
- **Placement**: Place the `IconButton` directly in the action bar row below the album cover (alongside Lyrics, Add to Playlist, and Favorite). This avoids fighting the album cover's drag-down-to-collapse or tap-to-toggle-lyrics gestures.
- **URL Format**:
  - Remote/YouTube Music song: `https://www.youtube.com/watch?v=${song.remoteVideoId}`
  - Local offline song (no `remoteVideoId`): fallback to YouTube search query `https://www.youtube.com/results?search_query=${Uri.encode("${song.title} ${song.artistName}")}` so the recipient can still listen on YouTube.

## Task List
### Phase 1: Implementation
- [ ] Task 1: Add Share intent launcher and Share button to `FullScreenPlayer`
### Checkpoint
- [ ] Build passes with `./gradlew.bat assembleDebug`
- [ ] Verify share intent launches the system chooser with correct YouTube URL

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Local songs without a YouTube ID share a broken URL | Low | Fallback to a YouTube search link with title + artist |
| Extra button crowds small screens | Low | Icons sized consistently (24-28dp) with standard Spotify spacing |
