# Todo: Song View Counts, Album Release Dates & Search Playlist Fallback

## Task 1: Extend `Song` Model with `viewCount: Long?` and Implement Count Formatter
**Description:** Add `val viewCount: Long? = null` to `com.mousikify.core.model.Song`. Create a reusable count formatting utility `formatPlayOrViewCount(viewCount: Long?, playCount: Int, isLocal: Boolean): String?` (or extension on `Song`) that converts views to compact human-readable strings (`1.2B views`, `3.4M views`, `450K views`, `850 views`) and local plays (`14 plays`), returning `null` when count is missing or zero.
**Acceptance criteria:**
- [x] `Song` data class contains `val viewCount: Long? = null` with backward compatibility for all callers
- [x] Count formatter correctly formats billions (`B`), millions (`M`), thousands (`K`), and sub-thousands
- [x] Count formatter formats local plays as `X plays` or `1 play` only when `isLocal && playCount > 0`
- [x] Returns `null` when neither view count nor local play count exists
**Verification:**
- [x] Unit tests for count formatting logic pass
- [x] Build succeeds: `./gradlew.bat compileDebugKotlin`
**Dependencies:** None
**Files touched:**
- `core/model/src/main/java/com/mousikify/core/model/Song.kt`
- `core/model/src/test/java/com/mousikify/core/model/SongCountFormatterTest.kt`
**Estimated scope:** Small (2 files)

---

## Task 2: Populate `viewCount` in Remote Streaming Track Extraction
**Description:** In `YouTubeMusicSearchPagingSource.kt` and `RemoteMusicRepository.kt`, populate `viewCount = it.viewCount.takeIf { it > 0 }` from NewPipe's `StreamInfoItem` during track conversion (`toSong()`).
**Acceptance criteria:**
- [x] `StreamInfoItem.toSong()` extracts `viewCount` when positive
- [x] Artist top songs in `RemoteMusicRepository.searchSongsByArtist` retain `viewCount`
- [x] Search paged results in `YouTubeMusicSearchPagingSource` retain `viewCount`
**Verification:**
- [x] Build succeeds: `./gradlew.bat compileDebugKotlin`
**Dependencies:** Task 1
**Files touched:**
- `core/media/src/main/java/com/mousikify/core/media/remote/YouTubeMusicSearchPagingSource.kt`
- `core/media/src/main/java/com/mousikify/core/media/remote/RemoteMusicRepository.kt`
**Estimated scope:** Small (2 files)

---

## Checkpoint 1: Foundations Complete
- [x] All models and data conversions compile: `./gradlew.bat compileDebugKotlin`
- [x] Model unit tests pass: `./gradlew.bat testDebugUnitTest`

---

## Task 3: Display View / Play Count in `TrackListItem` and `CompactSongRow`
**Description:** Update `TrackListItem` (in `Components.kt`) and `CompactSongRow` (in `YouTubeMusicArtistView.kt`) to render the formatted count in the subtitle line next to the artist name (e.g., `Artist Name • 2.4M views`).
**Acceptance criteria:**
- [x] In `TrackListItem`, when `displayCount` is present, display `"$artist • $displayCount"`
- [x] In `CompactSongRow`, when `displayCount` is present, display `"$artist • $displayCount"`
- [x] No layout regression or text clipping occurs on smaller screen widths
**Verification:**
- [x] Build succeeds: `./gradlew.bat compileDebugKotlin`
**Dependencies:** Task 1, Task 2
**Files touched:**
- `core/designsystem/src/main/java/com/mousikify/core/designsystem/component/Components.kt`
- `core/designsystem/src/main/java/com/mousikify/core/designsystem/component/YouTubeMusicArtistView.kt`
**Estimated scope:** Small (2 files)

---

## Task 4: Display Full Album Release Date in `YouTubeMusicAlbumView`
**Description:** In `YouTubeMusicAlbumView.kt`, display the full release date in the hero metadata header line (e.g., `Album • October 24, 2023 • 12 songs, 46 min`) and in the liner notes footer (`Released October 24, 2023`). Keep `AlbumCard` clean showing the release year.
**Acceptance criteria:**
- [x] Header metadata in `YouTubeMusicAlbumView` uses `album.releaseDate` if available, falling back to `album.year`
- [x] Liner notes footer displays `Released ${album.releaseDate ?: album.year}`
- [x] `AlbumCard` displays `album.year` to preserve compact card aesthetics
**Verification:**
- [x] Build succeeds: `./gradlew.bat compileDebugKotlin`
**Dependencies:** None
**Files touched:**
- `core/designsystem/src/main/java/com/mousikify/core/designsystem/component/YouTubeMusicAlbumView.kt`
**Estimated scope:** Small (1 file)

---

## Task 5: Populate `releaseDate` in `RemoteMusicRepository` Album Profiles
**Description:** When resolving remote album profiles in `RemoteMusicRepository.getRemoteAlbumProfile()`, extract and populate `releaseDate` from album/playlist metadata or track upload dates when available.
**Acceptance criteria:**
- [x] `getRemoteAlbumProfile()` assigns `releaseDate` on the returned `Album`
- [x] Local album profiles fall back gracefully to release year if no exact date is found
**Verification:**
- [x] Build succeeds: `./gradlew.bat compileDebugKotlin`
**Dependencies:** Task 4
**Files touched:**
- `core/media/src/main/java/com/mousikify/core/media/remote/RemoteMusicRepository.kt`
**Estimated scope:** Small (1 file)

---

## Checkpoint 2: UI Presentation & Album Metadata Complete
- [x] Build succeeds: `./gradlew.bat compileDebugKotlin`
- [x] Track rows display view/play counts in Album and Artist views

---

## Task 6: Community Playlist Fallback for Song Searches in `RemoteMusicRepository`
**Description:** In `RemoteMusicRepository.searchMusicQuick()`, add a fallback step when direct `MUSIC_SONGS` and `VIDEOS` search return 0 tracks. Query `searchPlaylists(query)`, inspect the top 1-2 matching playlists, retrieve their tracks, filter for matches, parse title/artist, and attribute the community uploader.
**Acceptance criteria:**
- [x] If direct songs and video searches return empty, search community playlists
- [x] Fetch tracks from top playlist(s) and filter by query terms
- [x] Fallback runs asynchronously without hanging the search request
- [x] Extracts view counts and durations for candidate tracks
**Verification:**
- [x] Build succeeds: `./gradlew.bat compileDebugKotlin`
**Dependencies:** Task 1, Task 2
**Files touched:**
- `core/media/src/main/java/com/mousikify/core/media/remote/RemoteMusicRepository.kt`
**Estimated scope:** Medium (1 file)

---

## Task 7: Parse Uploader / Song Attribution in Search Results
**Description:** When songs are surfaced from community playlists, parse `"Artist - Title"` formats (e.g., `The Weeknd - Blinding Lights (Acoustic)`) to set Title = `Blinding Lights (Acoustic)` and Artist = `The Weeknd • via ChannelName`. Wire this into `SearchViewModel` and `SearchScreen` so users can seamlessly play and queue them.
**Acceptance criteria:**
- [x] Regex/heuristic parser separates Artist and Title from typical YouTube playlist video titles
- [x] Subtitle formats as `"$artist • via $uploaderName"` with view count appended if present
- [x] Clicking play plays the song normally with full playback queue support
**Verification:**
- [x] Unit tests for title/uploader parsing pass
- [x] Build succeeds: `./gradlew.bat compileDebugKotlin`
**Dependencies:** Task 6
**Files touched:**
- `core/media/src/main/java/com/mousikify/core/media/remote/RemoteMusicRepository.kt`
- `core/media/src/test/java/com/mousikify/core/media/remote/TrackAttributionParserTest.kt`
- `feature/search/src/main/java/com/mousikify/feature/search/SearchViewModel.kt`
**Estimated scope:** Medium (3 files)

---

## Checkpoint 3: Complete Integration & Whole-App Verification
- [x] Compile all modules: `./gradlew.bat compileDebugKotlin`
- [x] Run all unit tests: `./gradlew.bat testDebugUnitTest`
- [x] Assemble debug APK: `./gradlew.bat assembleDebug`
