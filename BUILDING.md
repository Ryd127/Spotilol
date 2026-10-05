# Building Spotilol

Current source version: **1.1.9 (19)**  
Application ID: `com.project.lol`

This repository keeps Spotilol's original application identity and core behavior while adding build hardening, Android 16 KiB page-size compatibility, release verification, and security fixes around WebView/native bridge boundaries.

## Toolchain

- JDK 17
- Gradle 9.7.1 (via the committed Gradle Wrapper)
- Android SDK Platform 37
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- CMake 3.22.1
- minSdk 28
- targetSdk 36
- compileSdk 37

## Local build

Open the project in Android Studio and let SDK Manager install the requested components, or run `build-apk.ps1` on Windows.

Gradle outputs are written to:

- `app/build/outputs/apk/debug/`
- `app/build/outputs/apk/release/`

The debug APK uses the normal Android debug key.

A release APK is signed only when both of these are present:

- `keystore/keystore.properties`
- the keystore referenced by that properties file

Private signing keys and Firebase credentials are intentionally excluded by `.gitignore`.

## Release signing

The app reads the following properties from `keystore/keystore.properties`:

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

GitHub Actions uses these repository secrets for production signing:

- `SPOTILOL_KEYSTORE_B64`
- `SPOTILOL_STORE_PASSWORD`
- `SPOTILOL_KEY_ALIAS`
- `SPOTILOL_KEY_PASSWORD`

When those secrets are unavailable, CI creates an ephemeral test signing key so the release variant can still be built and verified. That fallback key is not suitable for distributing update-compatible production APKs.

The workflow currently pins the expected production certificate SHA-256 fingerprint to:

`A5:75:7D:4B:CA:7F:09:39:AC:CC:83:D8:E9:B7:30:17:B1:8C:6A:F4:5A:E5:F8:EB:A6:94:24:9F:80:EB:A9:85`

If the original project author builds with the original Spotilol signing key instead, the expected production fingerprint in `.github/workflows/build-apk.yml` must be updated to match that certificate.

### Update compatibility with the original app

The original APK used for comparison was signed with:

`59:1E:75:59:98:48:8E:5A:0F:C1:4A:A9:5B:C0:EB:CE:B6:46:50:82:19:E2:BF:07:3B:60:CF:28:58:E5:AC:E8`

Android only allows an APK to update an installed app when the signing identity is compatible. Therefore, a build signed with a different private key cannot directly update an installation signed by the original author's key.

## GitHub Actions gates

`.github/workflows/build-apk.yml` performs the complete release validation pipeline:

1. verifies the committed Gradle Wrapper JAR and Gradle distribution checksum;
2. installs the exact Android SDK, Build Tools, NDK and CMake versions;
3. records the build environment;
4. resolves and audits critical release dependencies;
5. runs the release R8 configuration analyzer;
6. runs focused unit tests for trusted WebView origins;
7. runs Android Lint on the release variant;
8. builds Debug and Release APKs;
9. validates package ID, version, SDK levels and debuggable state with `apkanalyzer`;
10. verifies 16 KiB ZIP alignment plus native ELF LOAD/RELRO alignment;
11. verifies APK signatures and, for production builds, the expected signing certificate;
12. creates a clean distributable package with the production APK, `SHA256SUMS.txt` and `RELEASE_INFO.txt`.

The final CI gate fails if any required audit, test, build, APK verification or packaging stage fails.

## 16 KiB native compatibility

The project packages native code for:

- `arm64-v8a`
- `armeabi-v7a`

The local AndroidX compatibility rebuilds plus the LAME and Opus JNI libraries are linked with explicit 16 KiB page-size support. CI independently checks the packaged APK so future toolchain or dependency changes cannot silently regress this requirement.

## WebView security hardening

The privileged `AndBridge` JavaScript interface is restricted to explicitly trusted Spotify origins. OAuth pages can remain inside the WebView for login, but do not receive the native bridge.

External deep links are validated against an exact HTTPS allowlist, and the native `nFetch` bridge is restricted to expected Spotify/CDN targets instead of acting as a generic network proxy.

Focused unit tests cover these origin rules and are required by CI.

## Automated GitHub releases

A tag named exactly `v<versionName>` triggers release publication after the build passes all gates.

For example:

```text
v1.1.9
```

The release job verifies that the tag matches the APK version, requires production signing, validates the certificate and SHA-256 checksum, then publishes:

- `Spotilol-<version>-production.apk`
- `SHA256SUMS.txt`
- `RELEASE_INFO.txt`

Release descriptions are intentionally left empty.
