# DocumentScanner — Round 15 FINAL

Kotlin + CameraX + OpenCV. Offline-first. No AI/ML/cloud APIs.

## Final round
- Release version 1.5.0 / versionCode 15
- Release validation helpers
- Final UI state model
- Play Store release checklist
- Store listing draft
- Final release-test source
- Existing Round 1–14 features retained

## What this ZIP is
This is the final source project prepared for release verification.

## What still requires your Android build environment
A signed APK/AAB cannot be legitimately embedded without your release signing key. Before publishing:
1. Open the project in Android Studio.
2. Sync Gradle.
3. Run unit tests.
4. Run instrumentation tests on a physical device.
5. Fix any environment/dependency errors.
6. Build a signed release APK/AAB.
7. Test the signed release build.
8. Complete Play Console declarations.

## Security
Do NOT upload:
- keystore files
- keystore passwords
- API keys
- private certificates
- local.properties

The app remains intentionally AI-free and cloud-free.

## GitHub Actions
The project must be built from the repository root. Do not run Gradle with `working-directory: app` because the module's plugin versions are defined by the root/settings Gradle configuration.
