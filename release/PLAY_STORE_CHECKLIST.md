# Play Store Release Checklist

## Build
- [ ] Run Gradle clean
- [ ] Run unit tests
- [ ] Run Android instrumentation tests
- [ ] Build release APK
- [ ] Build signed AAB
- [ ] Verify versionCode/versionName
- [ ] Test release build on at least 2 physical Android devices

## App
- [ ] Camera permission works
- [ ] Gallery import works
- [ ] Crop/rotate/filters work
- [ ] Multi-page reorder/delete works
- [ ] PDF export works
- [ ] Password PDF opens with correct password
- [ ] Share works
- [ ] Saved scans reopen
- [ ] No data is unexpectedly uploaded
- [ ] Large images do not crash the app

## Store
- [ ] App name
- [ ] Short description
- [ ] Full description
- [ ] 512x512 app icon
- [ ] Phone screenshots
- [ ] Privacy policy URL if required by the declared data practices
- [ ] Content rating
- [ ] Target audience
- [ ] Data Safety form
- [ ] App category
- [ ] Contact/support details

## Signing
Never put the release keystore, passwords, API keys, or signing secrets in GitHub.
