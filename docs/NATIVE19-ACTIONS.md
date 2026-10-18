# Native19: navigation and library actions

## Reference and boundaries

Reference: the user-supplied Spotify Android 9.1.92.3058 APK, SHA-256
`7f2a4b58eb273207be432b457dc365d53525dd2ec9c0b378fa6502af24b4251f`.
The earlier dimensional/motion inventory is in `SPOTIFY-9.1.92.3058-UI-REVIEW.md`.
This pass traced 41 selected resource constants to 23 DEX classes and inspected nine
targeted decompilation outputs. Those are analysis counts, not a claim to have read
all 103,801 classes or reconstructed Spotify's original source project.

Resource references and recovered call structures confirm separate Create entries,
including ordinary/collaborative playlists and conditional sections, and dedicated
Add-to-Playlist search, new-playlist and completion controls. Examples inspected:
`p.jqa`, `p.i7g`, `p.ctm`, `p.s01`, `p.oam`, `p.tch`, `p.tzl1`, `p.vxl1`, `p.vn50`.
JADX reported dependency/reconstruction warnings; generated Java is not authoritative
source. Server flags/account experiments cannot be determined from these resources.
The found Create enter/exit resources specify 300 ms translate/alpha transitions
with a 112 dp disappear distance; this does not prove that every live menu uses them.

Decompiled code, original fonts and original icon paths are not included in the app
or repository. Navigation/save/shuffle/more symbols are our own conventional vectors.
The new sheet uses Material3's animated modal component; it is not claimed to be
Spotify's identical transition implementation.

## Implemented changes

- Home/Search/Library have distinct selected/unselected vector states and tab semantics.
  Create opens a native action sheet; the Library plus opens the same sheet.
- Create opens a naming form for an ordinary or collaborative playlist. Only explicit
  confirmation sends a request. New playlists are private by default; collaborative
  creation is also private. Unsupported Premium/social/AI creation entries are not faked.
- Mini/full player and entity headers use plus/check save controls. A request is bound
  to the exact URI, not whichever track happens to be playing on completion. The check
  appears only after a successful response; a saved control opens contextual actions.
- Track overflow/long-press opens a native contextual sheet with save/remove and an
  Add-to-Playlist picker. The picker has search and new-playlist creation. It lists
  only playlists owned by the signed-in user or marked collaborative, up to 500,
  rather than offering read-only followed playlists as writable destinations.
- Selecting a destination sends one add request for the selected track/episode.
  Creating a destination inside the picker returns to the picker for explicit selection;
  it does not silently run two non-idempotent mutations.
- Library refresh invalidations coalesce behind an in-flight request; a late read cannot
  reverse a later confirmed save. A leftover guessed artwork URL rewrite was removed.
- Music/Podcast Home filters now filter actual content, including fallback shelves.
  The unsupported decorative Clips chip and search camera were removed.

## Data contract and safety

The existing signed-in WebView performs fixed-host Spotify Web API requests. The UI
receives display-only results, not authorization tokens. Current documented endpoints:

- `GET /v1/me/library/contains`, `PUT/DELETE /v1/me/library?uris=…`;
- `POST /v1/me/playlists` and `POST /v1/playlists/{id}/items`;
- `GET /v1/me`, `GET /v1/me/playlists` for editable destination filtering.

See Spotify's [save-library reference](https://developer.spotify.com/documentation/web-api/reference/save-library-items),
[create-playlist reference](https://developer.spotify.com/documentation/web-api/reference/create-playlist),
[add-items reference](https://developer.spotify.com/documentation/web-api/reference/add-items-to-playlist),
and [2026 migration guide](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide).
We do not use public player/queue endpoints to control a different Spotify device.
The existing Spotilol playback engine and transport commands are retained.

Commands validate URI type/ID, origin and names. Repeated dispatch of the same mutation
ID reuses its result, and pending operations have resource-specific locks. There is no
automatic mutation retry. Timeout/network/ambiguous server success or account change
produces an explicit unconfirmed result: check the library before trying again. No
green check, navigation or automatic second creation is fabricated on such a result.
Tokens remain in the existing WebView memory and are not written to native UI/logs.

## Verification and remaining scope

`ci/verify-native-actions.cjs` executes the real embedded action script against synthetic
responses: selected-target routing, confirmed-only saves, duplicate requests, stale
reads, denied scopes, malformed responses, timeout, late responses, account changes,
private/collaborative creation, editable destination filtering and origin/URI guards.
`NativeUiRegressionTest` includes rendered native action flows alongside existing
player/seek/search/navigation checks. These are fixtures, not authenticated account tests.

Actual Spotify account scopes and server acceptance still require on-device testing.
401/403/429 errors are surfaced, not presented as successful actions. No personal
account was modified during automated tests. The native picker is a single-destination
add flow, not Spotify's full multi-selection/membership editor; duplicate-track warnings,
removing from individual playlists, playlist editing, sharing, native downloads, queue
management and advanced account features are not newly implemented here. An explicit
Web Player action remains for unsupported features. Native copy is still primarily
Russian; this pass is not a completed Android localization rollout. UI/latency/FPS on
Vivo cannot be certified by a headless emulator or APK inspection.
