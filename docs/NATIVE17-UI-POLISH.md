# Native17: robustness pass on the native player prototype

17 October 2026. Version 1.2.4-native17, versionCode 47. This follows Native16, not a complete Spotify UI replacement or a claim of measured device frame rate.

## Changes

- An already-open player restored after configuration change starts in place, without a second entry animation. Normal opening/closing retains the Native16 150/200 ms translated layer.
- A new drag captures the actually displayed offset before cancelling an unfinished return. An epoch guard discards an older release callback after a newer drag/visibility change. Cancelled drags still spring back. The revealed background has an invisible, non-semantic input barrier until exit completes; it cannot accidentally navigate or play something underneath a pull.
- The background native shell's accessibility semantics are hidden while the expanded player is open. The player exposes a named traversal pane. This is not a claim of a fully audited TalkBack/keyboard focus trap.
- Artwork size now reserves room for the header, two title lines, seek controls, transport and footer. Very short screens and large text can scroll the expanded contents instead of squeezing touch targets or clipping controls. Transport controls can scroll horizontally in unusually narrow multi-window layouts. Artwork keeps the existing maximum of 365 dp; no audio polling is added.
- Seek preview shows the selected timestamp while dragging. Unknown-duration seeking is disabled. The slider session is recreated for a change in title, artist or duration; an old completion callback checks the latest display identity before sending a seek. Targets are finite and bounded to the known duration. The existing metadata bridge has no stable track URI in this display model, so two tracks with identical title, artist and duration cannot be distinguished here.

## Deliberately unchanged

Playback engine, WebView JavaScript, network/downloader, media/background services, progress reporting cadence, package ID and production signing identity. PC sources and author distribution are outside this change. Existing APKs are retained.

## Verification and limits

Pure policy tests cover drag cancellation/distance/velocity/density, progress smoothing, font-scaled mini-player heights, responsive artwork bounds and seek boundaries. The source guard checks the actual restored-state initialization, drag-offset capture, input barrier, scroll layout, slider identity and accessibility pane in addition to all 51 embedded scripts and existing metadata/navigation/bridge checks.

Android compilation, unit tests, lint, signed debug/release assembly and APK/native alignment checks must pass before delivery. Pure policy/source tests do not execute Compose pointer routing, render the actual phone screen, or measure frame pacing.

No connected Vivo or configured emulator is available for this pass. Remaining phone checks: rapidly open/close/reopen; interrupt the spring-back with another drag; pull then cancel/reverse; rotate while open; seek then change tracks; test zero-duration/loading state; increase system font size; test landscape scrolling and TalkBack; confirm next/play/background/offline playback remains correct. Do not claim these checks passed without running them.

The same production key and higher versionCode are intended for an in-place update over our Native16. Official Spotify remains a separate app. A saved older APK is not an in-place downgrade guarantee: Android normally rejects a lower versionCode.
