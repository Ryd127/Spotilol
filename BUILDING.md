# Building the repaired Spotilol RC

This project keeps the original Spotilol application ID and behavior. The build changes added during the repair work are only intended to make a clean checkout reproducibly buildable.

## Required toolchain

- JDK 17
- Gradle 9.7.1 (the included Gradle Wrapper downloads it)
- Android SDK Platform 37
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- CMake 3.22.1

The project targets Android API 36 and compiles against API 37.

## Windows / Android Studio

Open the project in Android Studio and let SDK Manager install the requested components, or run `build-apk.ps1` from PowerShell. The helper writes `local.properties`, installs missing SDK packages when `sdkmanager.bat` is available, then runs both `assembleDebug` and `assembleRelease`.

Produced files are under:

- `app/build/outputs/apk/debug/`
- `app/build/outputs/apk/release/`

The debug APK is automatically signed with the local Android debug key. A release APK is signed only when `keystore/keystore.properties` and the referenced key file are present.

## GitHub Actions

`.github/workflows/build-apk.yml` installs the exact SDK/NDK/CMake versions with `sdkmanager`, then builds Debug and Release separately. With no repository secrets it produces an installable debug APK and an unsigned release APK. Both Gradle logs and the SDK/environment diagnostics are uploaded even when compilation or verification fails, so the first failing CI run is actionable instead of losing the compiler output.

For a signed release, configure these GitHub Actions secrets:

- `SPOTILOL_KEYSTORE_B64` - base64 of the JKS/keystore file
- `SPOTILOL_STORE_PASSWORD`
- `SPOTILOL_KEY_ALIAS`
- `SPOTILOL_KEY_PASSWORD`

The workflow checks 16 KiB ZIP alignment plus PT_LOAD and GNU_RELRO layout for 64-bit native libraries, requires the debug APK (and any release built with configured signing secrets) to pass signature verification, and emits SHA-256 hashes. Debug, Release, and verification are independent diagnostic stages: their logs are always uploaded under `ci-logs/`, each rerun gets its own artifact name, and a final gate keeps the workflow red if any stage fails.

## Important signing note

The original APK supplied for comparison is signed with an APK Signature Scheme v2 signer whose certificate SHA-256 fingerprint is:

`59:1E:75:59:98:48:8E:5A:0F:C1:4A:A9:5B:C0:EB:CE:B6:46:50:82:19:E2:BF:07:3B:60:CF:28:58:E5:AC:E8`

Certificate subject: `CN=Spotilol, OU=Mobile, O=Spotilol, L=Unknown, ST=Unknown, C=US`.

A debug APK or an APK signed with a different key **cannot update an already-installed copy signed by the author's key**. Android will reject it as a signature mismatch. To install such a build you must uninstall the author-signed app first, which also removes its app-private data, or obtain/use the same private signing key as the original author.

Do not commit private signing keys or Firebase credentials. The existing `.gitignore` intentionally excludes them.
## Release dependency hardening

Part 15 pins the current stable `androidx.graphics:graphics-path` and `graphics-shapes`
line to `1.1.0`. The upstream 1.1.8 APK resolves `graphics-path`/`graphics-shapes`
`1.0.1`; its arm64 `libandroidx.graphics.path.so` has a GNU_RELRO end that is not
16 KiB aligned. AndroidX Graphics 1.1.0 is the current stable line and its native
build explicitly enables 16 KiB ELF page alignment.

DataStore is deliberately **not** force-upgraded here. The upstream APK resolves
DataStore `1.1.7`, while public reports also show RELRO/page-size problems in later
DataStore builds. Glance uses normal single-process Preferences DataStore in this
project, so changing or removing DataStore's native shared-counter binary without a
real device/build test would add more risk than it removes. The CI ELF/RELRO gate
therefore remains authoritative: the first real build must pass it before release.

The CI workflow writes `ci-logs/release-dependencies.log` with the full
`releaseRuntimeClasspath` plus `dependencyInsight` for Graphics, DataStore,
WorkManager, Room and Glance. APK verification also prints the packaged AndroidX
`META-INF/*.version` values, so the resolved release graph is visible from the first
real runner execution.

## Final RC build gates

The CI workflow also verifies the checked-in Gradle 9.7.1 wrapper JAR against Gradle's
official SHA-256 and confirms the wrapper distribution checksum before invoking Gradle.
AGP 9.4's standalone `:app:analyzeReleaseR8Config` task is run as an independent
release gate; its HTML report and the regular release mapping/config-analyzer outputs are
uploaded with the CI artifact.

Every produced APK is inspected with `apkanalyzer` before it is accepted. The package must
remain `com.project.lol`, version name/code must remain `1.1.8` / `18`, minSdk/targetSdk
must remain `28` / `36`, Debug must be debuggable and Release must not be debuggable.
These identity checks run in addition to signature verification and the 16 KiB ZIP/ELF/RELRO
gates, so an accidentally misconfigured variant cannot be mistaken for the final Spotilol RC.
