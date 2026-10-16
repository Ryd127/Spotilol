# Android 1.2.4 Native15 integration

16 October 2026. This branch continues the Android handoff from Ryd1, separately from the completed Windows port.

## Exact starting points

- Stable main: b2ae6400a711dd1cc119eee688cdbaf5a62f0f9b, unchanged.
- Last native branch: 78e531a0a3ac7c26dc6172b09bdda3d49809ea0a, Redesign14, versionCode 44.
- Upstream 1.2.0: a62e55dee71ecf558136d52958d89fac5ed6599b.
- Upstream 1.2.4: 5d20d8ffc0fc689407aceb065cb7ab37bb2b81fd.

Redesign14 was reconstructed from its pinned upstream, the SHA-256-verified Brotli patch and all twelve Python overlays in their original CI order. The 126 upstream file changes were then merged against that actual source tree with line endings normalized for comparison. Conflicts were reviewed individually.

The branch now contains the source that is compiled. CI no longer replaces it with another downloaded app tree or executes the historical overlays before Gradle. The old overlay inputs remain available as history, but are not current build instructions.

## Identity and installation

The user removed the previous experimental Spotilol and now has official Spotify installed. Native15 therefore uses applicationId com.project.lol, versionCode 45, versionName 1.2.4-native15. Artwork uses the app's own authority com.project.lol.artwork. The package and provider do not conflict with official Spotify.

The historical Vivo experiment used com.spotify.music and a Spotify-shaped provider authority. That identity is not used by this coexistence build. Origin Island recognition with the independent package is not confirmed. The original author's com.project.lol APK has a different signing certificate; it cannot be updated in place with this community key.

## Integration decisions

- Keep the guarded, race-aware transport and bounded DOM scheduler from the existing native branch. Do not restore the upstream multi-attempt pause loop.
- Adopt upstream track-bound metadata, new download/offline functions, sleep-timer manager, navigation-token handling and shared WebView host.
- Preserve the origin-scoped bridge and bounded native HTTP fetch. Extend its explicit method list for the new volume dialog and context sheet state.
- Route native position updates to both the sleep timer and native UI, so neither callback overwrites the other.
- Keep paced progress messages; seeking and restart remain immediate. A backward restart cannot cause a repeated full-status loop.
- Install the native snapshot hook from the new WebView host's actual player-ready callback.
- Restore online/offline hardware-key routing.
- Connect current-track favorite, shuffle and repeat controls to their existing transport commands instead of opening the web fallback.
- Normalize localized Spotify paths and recognize anonymous cards with repeated overlay/title links without borrowing artwork from adjacent cards.
- Replace the previous fixed R avatar initial with a generic profile icon.
- Accept the same Spotify artwork CDNs in the native mini-player and browsing interface.
- Keep the four new timer/battery analytics events safe when optional Firebase is absent.
- Register internal playback broadcasts with an explicit non-exported policy on every supported Android version.
- Keep standard HTTP caching without forcing the experimental WebView back/forward document cache.
- Use configuration-aware resources for device-info, theme-import and log-copy feedback in settings.

## Checks and boundaries

ci/verify-android.cjs validates embedded JavaScript syntax and exercises metadata pacing, seeks, restart, artwork updates, native play/navigation separation, bridge setup and installation identity.

The existing CI keeps unit tests, lint, debug/release compilation, production certificate verification, APK identity checks, zipalign and 16 KiB ELF checks. Build and artifact results must be recorded after the run completes, not inferred from source checks.

WebSecurityPolicyTest exercises the actual origin-scoped bridge policy, including the new UI commands, login-only access, frame restrictions, URL authority tricks and cookie-attribute injection. The first integration run correctly stopped on four nullable analytics calls before producing an APK; those calls are now guarded and covered by the source regression check.

No phone is connected to the local Android debugging bridge. UI fidelity, the previously reported audio click, Bluetooth/background behavior and Vivo-specific presentation require actual device verification. The screenshots captioned Home and Library in the handoff have opposite screens on their right sides; compare the screen content and selected bottom tab rather than the captions alone.

The native interface still uses the existing authorized Spotify WebView for playback. Advanced web-only actions retain an explicit fallback. No second audio engine or copied proprietary Spotify UI code is introduced.
