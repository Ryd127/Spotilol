# Native20 — targeted Canvas power saving and Artist sorting

Version: `1.2.4-native20`, code `50`; optional Vivo identity: `1.2.4-native20-vivo`.
This is the Android project, not the desktop port. Upstream base remains 1.2.4.

## Power saving

Removed the `setInterval` wrappers from both `PowerSave` and `WorkerNeutralize`.
The former stretched 100–9999 ms intervals; the latter changed every 250 ms
interval to 500 ms even with power saving disabled. Both lost callback arguments.
No global timer, timeout or animation-frame replacement is installed by these
modules now. Worker/service-worker preferences retain their existing scope.

A single `VideoPark` controller pauses videos only inside explicit Spotify
`data-testid="canvas-container"` / `data-testid="canvas"` containers. Muted,
looping or `object-fit:cover` alone are **not** sufficient. Audio, music videos,
podcasts and the separate `spl-canvas` player are not included by those heuristics.
Unknown future markup is left alone rather than guessed to be decorative.

With the existing `PowerSave` preference enabled, these decorative Canvas videos
remain paused in the foreground too. It remains disabled by default. Background
parking is independent of that preference and is connected to Activity stop/resume
as well as document visibility. Disabling the preference while still backgrounded
does not resume Canvas. Added/reclassified videos are handled by a bounded-lifetime
DOM observer; there is no new polling timer. Foreground/power-off disconnects it.

Sources, timecodes and autoplay attributes are not removed or reloaded. A saved
own `play` descriptor and preload attribute are restored without overwriting
Spotify replacements. Initially paused or detached videos are not started. A
changed source is not resumed from old state; a deliberate play request for its
new source can resume after leaving power saving. `blob:` sources are retained.

This avoids timer-induced responsiveness regressions. It is not a measured
promise of a particular battery saving or a fixed network track-switch latency.

## Sorting

The native playlist/album/Liked Songs screen now has an inline sort control and
matching bottom sheet: original, title ascending/descending and artist
ascending/descending. Choice is stored locally per route; the existing
`PlaylistSortEnabled` setting disables the control and restores display order.
Collation follows Android's current locale. Missing names stay last in both
directions; equal keys retain their original order. The original list is not
mutated, and opening/sorting adds no network requests. New native labels have
English/Russian resources (Artist reuses the existing translated resource);
untranslated labels follow Android's English fallback. This is not a completed
Android localization rollout.

The Web Player's existing columns menu also gains Artist, with playlist/album
metadata support for both `artists.items[].profile.name` and `artists[].name`,
including multiple artists. Existing `TITLE_AND_ARTIST` preferences still mean
title sort. Locale-prefixed routes are recognized. Fields are extracted from the
response item shape rather than the visible route, avoiding misclassification
when an older response settles after navigation. The existing pagination guard
is retained: later requests outside a cached range still reach the network.

Native sorting is **display-only for loaded rows**, not a remote playlist edit
or a new sorted playback queue. Next/previous continue to use the existing
Spotify context. Native and Web sort preferences are independent because native
detail browsing does not navigate the hidden React page. Existing native fetch
limits (playlist request 100, display/parser limits) are not expanded here.
Web sorting still batches at most 1000 items; pages beyond that retain the
existing page-local fallback, not a globally sorted unlimited collection.

## Verification

`ci/verify-android.cjs` includes `ci/verify-power-sort.cjs`, executing the real
embedded scripts with synthetic timers, DOM/media elements and API envelopes:

- scheduler identity/delays/extra arguments with both modules installed;
- strict Canvas selection, paused/playing state, method/preload preservation,
  source changes, `blob:` sources, lifecycle/visibility combinations, late DOM
  additions, reclassification, removal, repeated toggles and idempotent install;
- playlist/album Artist ASC/DESC, stable ties, null/multiple artists, legacy
  title keys, route-local persistence, locale routes, disabled/default behavior
  and pagination beyond the cached range (1001-row fixture).

`NativePlaylistSortTest` covers native comparator and preference parsing.
`NativeUiRegressionTest.nativeArtistSortSelectsRestoresAndKeepsOriginalData`
renders/clicks the sheet, checks selection and restored state, and captures
`artist-sort-fixture.png` alongside existing native action/player regression tests.

Release gates remain unit tests, Android Lint, standard/Vivo production builds,
isolated emulator UI tests, APK identity/signature and 16 KiB ZIP/ELF checks.
Record the actual gate outcomes with the delivered build; source-level tests are
not evidence that the production build or on-device playback has passed.

No personal account mutations or phone installation are part of these fixtures.
Real-account playback/Canvas and Vivo Island behavior still need a device check.
No SpotiDuck APK code/resources, forced English spoof or broad video heuristics
are copied. Connection diagnostics remain a separate task.
