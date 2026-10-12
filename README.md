# DPAD Market for Android

An Android app catalog for button-first devices. DPAD Market curates listings, reads the latest release metadata from each developer's GitHub repository, and lets Android's package installer handle installation and confirmation.

## Build

With Android SDK platform 36 and JDK 17 or newer:

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Catalog entries

The seed catalog is `app/src/main/assets/catalog.json`, schema version 1. Entries have a stable ID, expected Android package ID, developer, GitHub repository and releases API URL, category, short summary, curator note, SPDX license identifier, platform, and a plain-language D-pad support status.

To propose an app, open a pull request adding its metadata there. Include a public repository with downloadable APK release assets and verify its package ID and license against the upstream project. Describe compatibility accurately: distinguish remote/TV interfaces from app-wide D-pad support, and use `Needs device testing` when it has not been checked on actual hardware. A listing is a curator recommendation, not a security audit.

The app currently reads curated metadata bundled in the APK, then checks the linked GitHub `releases/latest` endpoint when an app's details are opened. Users can also add any public GitHub repository that publishes a direct APK asset; those entries remain local, persistent, and visibly unlisted. APK installation is delegated to Android `PackageInstaller`; the device presents its normal confirmation UI. DPAD Market does not rebuild, mirror, or silently install third-party packages.

## Safety model in this prototype

- Every app detail exposes its upstream repository and declared package ID.
- The installer requires the package ID in catalog metadata to match the APK.
- The download is served by GitHub over HTTPS and passed to Android's package installer.
- Android handles install confirmation and signing-certificate compatibility with any already-installed copy.
- Release hashes are not pinned yet, and the curated source list is not a code audit. The interface calls this out instead of implying verification.
