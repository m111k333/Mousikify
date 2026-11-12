# Implementation Plan: Song View Counts, Album Release Dates & Search Playlist Fallback

## Overview
This plan implements three targeted music discovery enhancements across Mousikify:
1. **Song Listening / View Times Display**: Display YouTube view counts (e.g., `2.4M views`) for streaming tracks and local play counts (e.g., `14 plays`) for local songs across Album View, Artist View, and Search result song rows.
2. **Album Release Date Propagation & Presentation**: Provide release date information on the Album Detail View (both in the hero metadata header and liner notes footer) while keeping Album Cards clean with release year.
3. **Community Playlist Fallback for Song Search**: When a direct song search returns no matches, automatically search top matching community playlists, parse and extract candidate songs, and attribute the uploader (e.g., `Artist • via UploaderName`).

---

## Architecture Decisions
- **`Song` Model Extension**: Add `val viewCount: Long? = null` to `core:model:Song.kt`. Because Room uses `SongEntity` (which already has local `playCount: Int`), adding an optional domain field `viewCount` in `Song` avoids Room schema bumps or migrations while enabling 64-bit remote YouTube view counts.
- **Formatting Standard**: A centralized formatting utility `formatPlayOrViewCount(viewCount: Long?, playCount: Int, isLocal: Boolean): String?` will output standard formatted strings (e.g., `1.2B views`, `14.5M views`, `320K views`, `850 views`, or `12 plays`), returning `null` when a track has 0 views or 0 local plays so the UI remains uncluttered.
- **Display Location**: As decided during grilling, view/play counts will appear on the secondary subtitle line alongside the artist (e.g., `Artist Name • 2.4M views`), preserving duration and action buttons on the right side.
- **Album Release Date Placement**: Full release date strings (e.g., `October 24, 2023`) will be displayed in `YouTubeMusicAlbumView` hero metadata and liner notes footer. `AlbumCard` will continue showing the release year to preserve carousel compactness.
- **Playlist Track Fallback Flow**: In `RemoteMusicRepository.searchMusicQuick()`, if both `MUSIC_SONGS` and `VIDEOS` return empty results, query `searchPlaylists(query)`. Fetch the tracks of the top matching playlist(s), identify matching tracks, parse `"Artist - Title"` patterns, and label the subtitle as `"$artist • via $uploaderName"`.

---

## Task List

### Phase 1: Domain & View Count Formatting Foundations
- [ ] Task 1: Extend `Song` model with `viewCount: Long?` and create centralized count formatter
- [ ] Task 2: Populate `viewCount` in `YouTubeMusicSearchPagingSource` and `RemoteMusicRepository`

### Checkpoint 1: Foundations Complete
- [ ] All models and data layers compile cleanly
- [ ] Count formatting unit tests pass

### Phase 2: UI Integration (Album, Artist & Search Views)
- [ ] Task 3: Update `TrackListItem` and `CompactSongRow` to display view/play count in subtitle
- [ ] Task 4: Display full release date in `YouTubeMusicAlbumView` (Header & Liner Notes)
- [ ] Task 5: Populate `releaseDate` in `RemoteMusicRepository` album resolution

### Checkpoint 2: UI & Album Presentation Verified
- [ ] Album view displays release date in header and footer
- [ ] Songs in album and artist views display formatted view/play counts

### Phase 3: Playlist Search Fallback for Songs
- [ ] Task 6: Implement playlist track fallback in `RemoteMusicRepository.searchMusicQuick`
- [ ] Task 7: Implement title/uploader parsing and integration in `SearchViewModel` & `SearchScreen`

### Checkpoint 3: Complete Verification
- [ ] Unit tests pass for fallback parsing and search logic
- [ ] Whole-app compilation and test suites pass

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
| :--- | :--- | :--- |
| Video view counts may exceed 32-bit integer limits | Medium | Use `Long?` for `viewCount` in `Song` to handle billions of views without integer overflow. |
| Fallback playlist track search adds network latency | Medium | Trigger fallback strictly when direct `MUSIC_SONGS` and `VIDEOS` searches return 0 results. Limit inspection to top 1-2 playlists with async execution. |
| Third-party playlist video titles are formatted unpredictably | Low | Use regex/heuristic splitting on ` - ` or `: ` with graceful fallback to raw title and uploader attribution. |
