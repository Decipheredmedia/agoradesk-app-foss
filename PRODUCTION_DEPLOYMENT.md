# Production Deployment Guide

This guide covers the complete setup and deployment process for the AgoraDesk/LocalMonero Android crypto exchange app in a production environment.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Initial Setup](#initial-setup)
3. [Configuration](#configuration)
4. [Security Checklist](#security-checklist)
5. [Building for Production](#building-for-production)
6. [Release Process](#release-process)
7. [Monitoring and Maintenance](#monitoring-and-maintenance)

## Prerequisites

### Required Software

- **Flutter SDK**: Version 3.0.0 or higher
  - Install from: https://docs.flutter.dev/get-started/install
  - Or use the submodule: `git submodule update --init --recursive`
- **Android Studio**: Latest stable version
- **Java Development Kit (JDK)**: Version 17
- **Android SDK**: API level 34 (compileSdkVersion)
  - Minimum API level: 23
  - Target API level: 33

### Required Accounts and Keys

1. **MapBox Account**: For reverse geocoding functionality
   - Sign up at: https://www.mapbox.com/
   - Free tier available for moderate usage

2. **Firebase Project** (Optional, for push notifications):
   - Google Play version: Required
   - F-Droid/FOSS version: Not required (uses polling instead)

3. **Signing Keys**: For release builds
   - Create production keystores for both apps

4. **Sentry Account** (Optional): For error tracking and monitoring
   - Sign up at: https://sentry.io/

## Initial Setup

### 1. Clone the Repository

```bash
git clone https://github.com/Decipheredmedia/agoradesk-app-foss.git
cd agoradesk-app-foss
```

### 2. Initialize Flutter Submodule (if using submodule)

```bash
git submodule update --init --recursive
```

### 3. Install Dependencies

```bash
flutter pub get
```

### 4. Generate Code

```bash
dart run build_runner build --delete-conflicting-outputs
```

## Configuration

### 1. API Keys Configuration

#### MapBox API Key

1. Copy the template file:
```bash
cp lib/keys/keys.dart.template lib/keys/keys.dart
```

2. Edit `lib/keys/keys.dart` and replace the placeholder:
```dart
const String keysMapToken = 'your_actual_mapbox_api_key_here';
```

**Security Best Practice**: For CI/CD, use environment variables:
```bash
flutter build apk --dart-define=MAPBOX_API_KEY=your_key_here
```

### 2. Signing Configuration

#### Create Keystores

For **AgoraDesk**:
```bash
keytool -genkey -v -keystore agoradesk-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias agoradesk
```

For **LocalMonero**:
```bash
keytool -genkey -v -keystore localmonero-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias localmonero
```

#### Configure key.properties

Create `android/key.properties`:
```properties
# AgoraDesk signing configuration
adStoreFile=/path/to/agoradesk-release-key.jks
adStorePassword=your_keystore_password
adKeyAlias=agoradesk
adKeyPassword=your_key_password

# LocalMonero signing configuration
lmStoreFile=/path/to/localmonero-release-key.jks
lmStorePassword=your_keystore_password
lmKeyAlias=localmonero
lmKeyPassword=your_key_password
```

**⚠️ SECURITY WARNING**: Never commit `key.properties` or `.jks` files to version control!

### 3. Firebase Configuration (for Google Play builds)

If including Firebase Cloud Messaging (FCM):

1. Create Firebase projects:
   - One for AgoraDesk
   - One for LocalMonero

2. Download configuration files:
   - `google-services.json` for Android
   - Place in `android/app/src/agoradesk/` and `android/app/src/localmonero/`

3. Generate Firebase options:
```bash
# Follow instructions at: https://firebase.flutter.dev/docs/cli
flutterfire configure
```

## Security Checklist

Before production deployment, ensure:

- [ ] All API keys are stored securely (not in source code)
- [ ] Signing keystores are backed up securely
- [ ] `key.properties` is in `.gitignore` and not committed
- [ ] `lib/keys/keys.dart` contains production keys (not placeholders)
- [ ] Firebase configuration files are environment-specific
- [ ] SSL/TLS certificate pinning is configured (if applicable)
- [ ] ProGuard/R8 rules are properly configured for code obfuscation
- [ ] Security scanning has been performed (CodeQL, dependency checks)
- [ ] Network security configuration is production-ready
- [ ] Debug logging is disabled in release builds
- [ ] Backup keys are stored in a secure location (KMS, vault, etc.)

### Security Features Already Implemented

✅ **Privacy-First Notifications**: All notifications are translated client-side, not via FCM  
✅ **Proxy Support**: Custom HTTP, SOCKS4, and SOCKS5 proxy configuration  
✅ **Secure Storage**: Uses flutter_secure_storage for sensitive data  
✅ **Biometric Authentication**: Local auth support  
✅ **Certificate Pinning**: Can be configured for enhanced security  

## Building for Production

### FOSS Builds (F-Droid, Direct APK)

These builds exclude Firebase and use polling for notifications:

#### AgoraDesk FOSS APK:
```bash
flutter build apk --flavor agoradesk \
  --dart-define=app.flavor=agoradesk \
  --dart-define=app.includeFcm=false \
  --dart-define=MAPBOX_API_KEY=your_key
```

#### LocalMonero FOSS APK:
```bash
flutter build apk --flavor localmonero \
  --dart-define=app.flavor=localmonero \
  --dart-define=app.includeFcm=false \
  --dart-define=MAPBOX_API_KEY=your_key
```

#### Using Make (recommended):
```bash
make build-foss-apk-ad  # AgoraDesk
make build-foss-apk-lm  # LocalMonero
make build-foss-apk-all # Both apps
```

### Google Play Builds

These builds include Firebase Cloud Messaging:

#### AgoraDesk App Bundle:
```bash
flutter build appbundle --flavor agoradesk \
  --dart-define=app.flavor=agoradesk \
  --dart-define=MAPBOX_API_KEY=your_key
```

#### LocalMonero App Bundle:
```bash
flutter build appbundle --flavor localmonero \
  --dart-define=app.flavor=localmonero \
  --dart-define=MAPBOX_API_KEY=your_key
```

#### Using Make:
```bash
make build-bundle-ad  # AgoraDesk
make build-bundle-lm  # LocalMonero
make build-bundle-all # Both apps
```

### Build Output Locations

- **APKs**: `build/app/outputs/apk/<flavor>/release/`
- **App Bundles**: `build/app/outputs/bundle/<flavor>Release/`

## Release Process

### 1. Pre-Release Checklist

- [ ] Version number updated in `pubspec.yaml`
- [ ] Changelog updated
- [ ] All tests passing: `flutter test`
- [ ] Code analysis clean: `flutter analyze`
- [ ] Security scan completed
- [ ] Beta testing completed
- [ ] App store assets prepared (screenshots, descriptions)

### 2. Version Management

Update version in `pubspec.yaml`:
```yaml
version: 1.1.40+140  # format: major.minor.patch+buildNumber
```

### 3. Build Release Artifacts

```bash
# For all platforms and flavors
make build-all

# Or individually
make build-bundle-ad    # AgoraDesk bundle
make build-bundle-lm    # LocalMonero bundle
make build-foss-apk-ad  # AgoraDesk FOSS APK
make build-foss-apk-lm  # LocalMonero FOSS APK
```

### 4. Distribution Channels

#### Google Play Store

1. Build app bundle:
```bash
make build-bundle-ad  # or build-bundle-lm
```

2. Upload via Google Play Console or use Fastlane:
```bash
cd android
fastlane supply --skip_upload_changelogs=true --track=internal
```

#### F-Droid

F-Droid builds automatically from the repository. Ensure:
- FOSS build flags are properly set
- No proprietary dependencies
- Reproducible builds when possible

Update Flutter submodule for F-Droid:
```bash
git submodule update --rebase --remote
```

#### Direct APK Download (GitHub Releases)

1. Build FOSS APKs:
```bash
make build-foss-apk-all
```

2. Create GitHub release and upload APKs
3. Include SHA-256 checksums

### 5. iOS Release (if applicable)

```bash
make build-ios-ad  # AgoraDesk
make build-ios-lm  # LocalMonero
```

Deploy to TestFlight:
```bash
cd ios
fastlane deploy_ios testflight:true
```

## Monitoring and Maintenance

### Error Tracking

If using Sentry, configure in the app initialization:
- Set DSN in environment variables
- Configure release tracking
- Set up alerts for critical errors

### Analytics

The app uses Plausible Analytics for privacy-friendly analytics.
- No personal data collection
- Self-hosted option available

### Update Checks

For FOSS builds, enable update checking:
```bash
flutter build apk --dart-define=app.checkUpdates=true
```

### Regular Maintenance Tasks

- **Weekly**: Review error reports and crash analytics
- **Monthly**: Update dependencies and security patches
- **Quarterly**: Review and update third-party service configurations
- **Annually**: Rotate signing keys (if needed), review security policies

### Dependency Updates

```bash
# Check for outdated packages
flutter pub outdated

# Update dependencies
flutter pub upgrade

# Regenerate code
dart run build_runner build --delete-conflicting-outputs
```

## Troubleshooting

### Build Failures

1. **Clean build**:
```bash
make cleanf
```

2. **Verify Flutter installation**:
```bash
flutter doctor -v
```

3. **Check signing configuration**:
```bash
cat android/key.properties
```

### Runtime Issues

1. **Check logs**:
```bash
flutter logs
# or
adb logcat
```

2. **Verify API keys are set correctly**
3. **Check Firebase configuration** (for non-FOSS builds)

### Common Issues

**Issue**: "MapBox API key not found"  
**Solution**: Ensure `lib/keys/keys.dart` exists with valid key

**Issue**: "Signing config not found"  
**Solution**: Verify `android/key.properties` exists and paths are correct

**Issue**: "Firebase initialization failed"  
**Solution**: Check that `google-services.json` is in the correct flavor directory

## Additional Resources

- [Flutter Documentation](https://docs.flutter.dev/)
- [Android Developer Guide](https://developer.android.com/)
- [F-Droid Documentation](https://f-droid.org/docs/)
- [Firebase Setup](https://firebase.google.com/docs)
- [Original Blog: Publishing Flutter App on F-Droid](https://localmonero.co/devblog/publish-flutter-app-fdroid)

## Support

For issues or questions:
- Check existing GitHub issues
- Review the main README.md
- Consult the Notifications.md for push notification setup

## License

See LICENSE file in the repository root.
