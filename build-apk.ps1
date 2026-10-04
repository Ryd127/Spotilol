$ErrorActionPreference = 'Stop'

Write-Host 'Spotilol APK build helper' -ForegroundColor Cyan

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java is not available. Install JDK 17 (Temurin recommended) or Android Studio with JDK 17.'
}

$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk -and $env:LOCALAPPDATA) {
    $candidate = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $candidate) { $sdk = $candidate }
}
if (-not $sdk -or -not (Test-Path $sdk)) {
    throw 'Android SDK not found. Install Android Studio/SDK and set ANDROID_SDK_ROOT, or use the GitHub Actions workflow included in this project.'
}

$escapedSdk = $sdk -replace '\\','\\\\'
"sdk.dir=$escapedSdk" | Set-Content -Encoding ASCII local.properties
Write-Host "Android SDK: $sdk"

$sdkManager = Get-ChildItem -Path (Join-Path $sdk 'cmdline-tools') -Filter sdkmanager.bat -Recurse -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($sdkManager) {
    $packages = @(
        'platform-tools',
        'platforms;android-37',
        'build-tools;36.0.0',
        'ndk;28.2.13676358',
        'cmake;3.22.1'
    )
    Write-Host 'Ensuring required Android SDK packages are installed...'
    & $sdkManager.FullName $packages
    if ($LASTEXITCODE -ne 0) {
        throw "sdkmanager failed. If licenses are not accepted, run `"$($sdkManager.FullName)`" --licenses and retry."
    }
} else {
    Write-Warning 'sdkmanager.bat was not found; assuming the required SDK/NDK/CMake packages are already installed.'
}

if (-not (Test-Path '.\gradle\wrapper\gradle-wrapper.jar')) {
    throw 'gradle/wrapper/gradle-wrapper.jar is missing. Restore it from the project archive before building.'
}

Write-Host 'Building debug and release APKs...' -ForegroundColor Cyan
& .\gradlew.bat --no-daemon --stacktrace --warning-mode all :app:assembleDebug :app:assembleRelease
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

$apks = Get-ChildItem -Path '.\app\build\outputs\apk' -Filter '*.apk' -Recurse
if (-not $apks) { throw 'Gradle finished but no APK was found.' }
Write-Host ''
Write-Host 'Build outputs:' -ForegroundColor Green
$apks | ForEach-Object { Write-Host $_.FullName }
Write-Host ''
Write-Host 'Debug APK is signed with the local Android debug key and is installable.'
Write-Host 'Release APK is signed only when keystore/keystore.properties and the referenced keystore are present.'
