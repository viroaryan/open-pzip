# Releasing pzip

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, and update the in-app and website changelogs.
2. Run `:app:testDebugUnitTest`, `:app:lintDebug` and a signed `:app:assembleRelease`.
3. Keep the signing keystore and its passwords outside this repository and back them up securely. Never publish them or change the signing key between updates.
   On Windows, `scripts/build-release.ps1` reads `%USERPROFILE%\.pzip\release-signing.json` with keys `keystore`, `alias`, and `password`, then stages signed APKs and checksums under ignored `release-dist/`.
4. Publish the universal and architecture-specific APKs to `viroaryan/pzip-releases` as GitHub Release assets named `pzip-universal.apk`, `pzip-arm64-v8a.apk`, `pzip-armeabi-v7a.apk` and `pzip-x86_64.apk`. Add `SHA256SUMS.txt` and concise release notes. A `v1.0.0` tag matches the current app version.
5. Download an APK from the public release, verify its checksum and signature, and test a clean install and an upgrade from the prior production version.

The website reads the newest GitHub release automatically, so it requires no redeploy for new APK assets. A release build without `PZIP_KEYSTORE_PATH`, `PZIP_KEYSTORE_PASSWORD`, `PZIP_KEY_ALIAS` and `PZIP_KEY_PASSWORD` is unsigned and must not be published.
