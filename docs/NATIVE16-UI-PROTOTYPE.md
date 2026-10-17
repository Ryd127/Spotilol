# Native16: first Spotify-referenced UI prototype

17 October 2026. Version 1.2.4-native16, versionCode 46. This is a deliberately scoped visual iteration, not a complete reproduction of Spotify.

## Scope

- Compact native transport bar: 56 dp at ordinary font scaling, 40 dp cover, 8 dp container radius, 4 dp cover radius, 13 sp title/artist with 16 sp line height. Large text uses at least 68 dp and grows further when necessary rather than clipping two lines.
- The 2 dp progress track lives inside the transport container. Short ordinary position updates receive 200 ms visual smoothing. Seeking, backward restart, a new track identity and paused updates do not tween across the song. No progress timer or extra WebView polling is introduced.
- Native toolbar controls, filter pills and the main play button use independent 200 ms press feedback, scaling to 0.9 with cubic-bezier (0.3, 0, 0, 1). Commands still run from their normal click callbacks, not from animation completion.
- The large player uses one translated graphics layer for entry, drag, return and dismissal. Simple entry/exit use 150/200 ms with quadratic deceleration, matching the simple resource path found in the reference APK. This does not claim to reproduce its alternate shared-element transitions.
- Dragging the header/artwork moves the entire player. Cancelled and short gestures spring back; sufficient distance/downward velocity dismisses; a deliberate upward reversal returns. Thresholds are density-aware. Velocity is tracked from accumulated finger movement because local coordinates belong to the moving handle.
- The gesture detector is attached only to the header and artwork, not the seek slider or transport controls. It uses the vertical drag detector's axis arbitration.
- Mini-player and current-track title/artist use the system sans-serif font for this trial. Spotify Mix font files and decompiled implementation are not bundled. This is not yet exact typography or an icon replacement.

## Separation and rollback

The audio engine, WebView scripts, metadata pacing, network code, Android media service and downloader are unchanged. Package ID remains com.project.lol. Native15 remains at release/Spotilol-1.2.4-native15-production.apk, SHA-256 70390c75ae574f31c782a5dce14c2d830fedc99fc9c7812a3c8c9189f60a69fb.

Native16 is signed with the same configured community production key and increments the version code, so it is intended to update Native15 without clearing its app data. It still coexists with official Spotify. Android ordinarily rejects installing a lower versionCode over a newer one; the saved Native15 APK is a fallback build, not a promise of an in-place downgrade. Do not uninstall or clear data just to roll back without considering saved downloads/session data.

## Verification

PlayerSheetPolicyTest covers cancellation, short/long pulls, fling travel, upward reversal, density scaling, invalid geometry, font scaling and progress seek/restart behavior. Existing trusted-origin tests and the 51-script integration checks remain enabled. The source guard additionally checks the real movement layer, cancellation handler, accessible progress and the absence of audio-bridge commands in the motion helper.

The Android CI must still pass compilation, unit tests, lint, signing and APK/native alignment verification before a file is delivered. Source-only checks do not prove Compose gesture routing or device smoothness.

Required Vivo checks: open/close repeatedly and rapidly reverse direction; pull and cancel; swipe downward and then reverse upward; drag the seek slider without dismissing the player; tap next/play/favorite while press feedback runs; test long names, narrow widths and large text; verify playback/background behavior. No fixed 144 FPS claim is made.

Reference evidence and limits: docs/SPOTIFY-9.1.92.3058-UI-REVIEW.md. The proprietary APK and extracted/decompiled materials remain in the ignored build analysis directory and are not part of this build's sources.
