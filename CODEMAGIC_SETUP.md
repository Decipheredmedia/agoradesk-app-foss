# Codemagic CI/CD — Setup Guide

This document describes every environment variable, secret, and configuration
step required to run the five `codemagic.yaml` workflows without manual
intervention.

---

## 1. Prerequisites

| Tool | Minimum version |
|------|----------------|
| Flutter | stable channel (pinned in `codemagic.yaml`) |
| Xcode | Latest (M1 Mac instance) |
| CocoaPods | Default Codemagic version |
| Java | 17 (set by Codemagic Android instance) |

---

## 2. Codemagic Environment Variable Groups

Create the following groups in **Codemagic → Teams → Environment variables**.

### Group: `android_signing`

| Variable | Description |
|----------|-------------|
| `AD_KEYSTORE` | Base64-encoded AgoraDesk `.jks` keystore file.<br>`base64 -i agoradesk-keystore.jks | pbcopy` |
| `AD_KEY_ALIAS` | Key alias inside the AgoraDesk keystore |
| `AD_KEY_PASSWORD` | Private key password |
| `AD_STORE_PASSWORD` | Keystore password |
| `LM_KEYSTORE` | Base64-encoded LocalMonero `.jks` keystore file |
| `LM_KEY_ALIAS` | Key alias inside the LocalMonero keystore |
| `LM_KEY_PASSWORD` | Private key password |
| `LM_STORE_PASSWORD` | Keystore password |

### Group: `ios_signing`

| Variable | Description |
|----------|-------------|
| `APP_STORE_CONNECT_KEY_IDENTIFIER` | 10-character key ID from App Store Connect → Users → Keys |
| `APP_STORE_CONNECT_ISSUER_ID` | UUID from the same page |
| `APP_STORE_CONNECT_PRIVATE_KEY` | Full `.p8` key content (PEM format) |
| `CERTIFICATE_PRIVATE_KEY` | Apple Distribution certificate private key (PEM) |

> Codemagic uses these variables to fetch the provisioning profiles and
> certificates automatically via the App Store Connect API.

### Group: `firebase`

| Variable | Description |
|----------|-------------|
| `AGORADESK_GOOGLE_SERVICES_JSON` | Base64 of `google-services.json` for the `agoradesk` flavor |
| `LOCALMONERO_GOOGLE_SERVICES_JSON` | Base64 of `google-services.json` for the `localmonero` flavor |
| `FIREBASE_OPTIONS_AGORADESK` | Base64 of `lib/firebase_options_agoradesk.dart` |
| `FIREBASE_OPTIONS_LOCALMONERO` | Base64 of `lib/firebase_options_localmonero.dart` |
| `AGORADESK_GOOGLESERVICE_INFO_PLIST` | Base64 of `GoogleService-Info.plist` for iOS AgoraDesk |
| `LOCALMONERO_GOOGLESERVICE_INFO_PLIST` | Base64 of `GoogleService-Info.plist` for iOS LocalMonero |

> FOSS builds (`foss-apk-build` workflow) do **not** require the Firebase
> group. FCM is disabled via `--dart-define=app.includeFcm=false`.

### Group: `app_secrets`

| Variable | Description |
|----------|-------------|
| `MAPBOX_KEY` | MapBox public token from https://account.mapbox.com/ |
| `SENTRY_DSN` | *(Optional)* Sentry DSN for crash reporting |

### Group: `store_credentials`

| Variable | Description |
|----------|-------------|
| `GCLOUD_SERVICE_ACCOUNT_CREDENTIALS` | Google Play service-account JSON (base64) with `releases` permission |
| `SLACK_WEBHOOK_URL` | *(Optional)* Incoming Webhook URL for Slack build notifications |

---

## 3. iOS Code Signing

1. In **Codemagic → App → Workflows → ios-agoradesk-release → Distribution**:
   - Distribution type: `App Store`
   - Bundle identifier: `com.agoradesk.app`
2. Repeat for `ios-localmonero-release` with bundle identifier `co.localmonero.app`.
3. Codemagic will automatically match provisioning profiles using the App Store
   Connect API credentials from the `ios_signing` group.

### Associated Domains (Universal Links)

Both iOS targets use Associated Domains for Universal Links.  
The entitlements files are already committed:

- `ios/Runner/Runner.entitlements` → `applinks:agoradesk.com`, `applinks:localmonero.co`
- `ios/Runner/Runner-localmonero.entitlements` → `applinks:localmonero.co`

Ensure the Apple Developer portal has **Associated Domains** enabled for both
App IDs (`com.agoradesk.app` and `co.localmonero.app`).

---

## 4. Android App Links (Deep Links)

The `assetlinks.json` file must be hosted at:

```
https://agoradesk.com/.well-known/assetlinks.json
https://localmonero.co/.well-known/assetlinks.json
```

Each file must reference the SHA-256 fingerprint of the respective signing
certificate.  Generate it with:

```bash
keytool -list -v -keystore agoradesk-keystore.jks -alias <alias>
```

---

## 5. Firebase Per-Flavor Setup

### Android

Each flavor has its own `google-services.json` placed at:

```
android/app/src/agoradesk/google-services.json    # com.agoradesk.app
android/app/src/localmonero/google-services.json  # co.localmonero.app
```

These are injected by CI from the `firebase` env group (base64 decoded).

### iOS

```
ios/firebase/agoradesk/GoogleService-Info.plist    # com.agoradesk.app
ios/firebase/localmonero/GoogleService-Info.plist  # co.localmonero.app
```

### Dart options files

```
lib/firebase_options_agoradesk.dart
lib/firebase_options_localmonero.dart
```

Generate them with:
```bash
flutterfire configure --project=<firebase-project-id>
```

---

## 6. FOSS Build — FCM-Free Path

The `foss-apk-build` workflow builds both flavors with:

```
--dart-define=app.includeFcm=false
```

This matches the existing logic in `lib/main.dart`:

```dart
bool includeFcm = includeFcmString != 'false' || Platform.isIOS;
```

When FCM is excluded, the app starts a foreground polling service instead of
registering with Firebase Cloud Messaging.  No `google-services.json` or
Firebase Dart option files are required.

---

## 7. Automatic Versioning

Version name and build number are read from `pubspec.yaml`:

```yaml
version: 1.1.39+139  # name=1.1.39  code=139
```

In Codemagic, `CM_BUILD_NUMBER` overrides the build code to produce unique
per-build numbers.  Bump the version string in `pubspec.yaml` for each release.

---

## 8. Triggering

All five workflows trigger on **Git tag push** matching `v*` (e.g. `v1.1.40`):

```bash
git tag v1.1.40
git push origin v1.1.40
```

---

## 9. Final Release Checklist

- [ ] `AD_KEYSTORE` and `LM_KEYSTORE` uploaded (base64)
- [ ] `android/key.properties` values match keystore aliases & passwords
- [ ] Firebase `google-services.json` files for both flavors encoded and saved
- [ ] Firebase Dart option files generated and encoded
- [ ] `GoogleService-Info.plist` files for both iOS flavors encoded and saved
- [ ] `MAPBOX_KEY` set in `app_secrets` group
- [ ] `GCLOUD_SERVICE_ACCOUNT_CREDENTIALS` set (Play Store publishing)
- [ ] App Store Connect API key uploaded (iOS publishing)
- [ ] Associated Domains enabled in Apple Developer portal
- [ ] `assetlinks.json` deployed to both domains
- [ ] `pubspec.yaml` version bumped before tagging
- [ ] Tag pushed → all 5 workflows triggered → artifacts signed → stores updated
