# Native18 recovery and verification scope

17 October 2026. Development record following Ryd1's real-device Native17 failure report. This is not a completed Vivo acceptance report.

## Confirmed faults and architecture correction

The retained 31-page handoff (parts 1–4) was reread, including the original-versus-prototype screenshots. It already recorded missing artwork, incomplete native feature parity and the distinction between successful CI and actual Vivo behavior. Native16/17 visual polishing did not close those underlying issues.

- Entity pages used a generic DOM link list when no playlist rows were found. Artist routes were not even included in the track-row condition. Native18 uses the existing authenticated query/parser layer for artist top tracks, albums, playlists and search. Results have an explicit native route identity, a bounded cache, cancellation/timeout handling and rejection of late responses. Arbitrary recommendations/sidebar artists are not a fallback for track lists.
- Native query requests return their own result without consuming or impersonating Android Auto callback requests. The legacy Auto callback path is preserved and separately exercised. Spotify credentials remain inside the trusted WebView; no second player or OAuth client is introduced.
- Main is now the actual singleTask launcher entry. First-run permissions still use onboarding; the old compulsory four-second splash delay is removed. The warm-launch scenario is included in Android instrumentation tests.
- The update checker no longer parses the patch component of `1.2.4-native18` as zero and mistakes upstream `1.2.4` for an update. Numeric-core comparison is tested, including Vivo/build suffixes and malformed versions. The isolated launcher fixture suppresses unrelated remote update checks.
- The player gradient previously contained transparency and revealed the browse page. It now has an opaque base. Native transport icons are independently drawn conventional filled symbols; artwork/source code from the proprietary Spotify APK is not bundled.
- The former broad live-page crossfade is replaced by a single-tree, short translated arrival layer. Native route changes display loading for the intended route rather than stale page contents. Browse reads are single-flight and faster only while waiting on a request; background native UI polling is suppressed.
- Artwork is no longer rewritten to a guessed CDN key. Track-bound metadata can supply a real cover. Source sets/query image sources prefer larger images and exclude known playback/equalizer placeholders. A shared 24 MiB, four-request artwork pipeline coalesces requests, bounds response bytes/decoded dimensions and does not downsample 64/640-pixel covers again.
- Seeking uses the real native range setter and input/change events rather than assigning React's tracked property and adding an unexplained millisecond. This is tested against a React-like controlled range. The native slider has a thin track and its own small recomposition boundary. Its fixed requested-position preview expires after 1.6 seconds; no moving fake playback clock is introduced. This does not prove audible seek latency on a phone.
- Expanded player restoration/drag cancellation, large-text scrolling, nested navigation/back, query entry and visual screenshots are included in the new emulator test suite. Fixtures are synthetic and clearly named; they do not establish authenticated Spotify or OriginOS correctness.

## Two explicit identities

The old Vivo success depended on package com.spotify.music. Native15 changed to com.project.lol for coexistence with the user's newly installed official Spotify; that removed the known successful identity condition. Native18 retains the ordinary independent build and restores a separately named Vivo build using -PvivoIsland=true. The same production certificate and versionCode 48 are used; Vivo has versionName 1.2.4-native18-vivo. Namespace remains com.project.lol and the provider authority remains applicationId-scoped.

The Vivo APK conflicts with installed official Spotify because they share an application ID and have different signatures. Do not uninstall either app or erase phone data automatically. The Vivo HTTPS artwork metadata key is still present, but actual Origin Island artwork/control behavior requires OriginOS testing. No universal OEM bypass or simultaneous official-Spotify coexistence claim is made.

## Verification requirements

Both production identities must pass compilation, all unit/data checks, lint, signing and APK/native alignment gates. A separate headless API35 x86_64 emulator runs the actual Compose UI and launcher scenarios with isolated fixture data and captures screenshots. Its ABI override is test-only; phone release APKs remain arm64-v8a + armeabi-v7a. No personal session, passwords or keystores are part of the tests or source.

Local source checks exercise 51 embedded scripts plus real browse/search response parsers, route races, request timeouts, largest-image selection, preserved Android Auto callbacks and synchronous React-compatible seek dispatch. Java policy tests include image URL authority tricks, placeholder filtering and decoding bounds. Final binary/test outcomes must be recorded after the workflow finishes; intermediate step conclusions are not proof of passing gates.

Still requires a real Vivo session: authenticated response shapes/available content, actual image CDN fetches for the user's account, audible seek/next latency, sound artifacts, Bluetooth/background/offline/lyrics/video regression checks, Origin Island and measured frame pacing. API failures are surfaced rather than replaced with plausible unrelated content. Full Spotify-native feature/design parity is not claimed.

Stable main, historical APKs, Windows sources and author distribution are unchanged. Current build input remains the checked-in source, not the historical hybrid overlay scripts.
