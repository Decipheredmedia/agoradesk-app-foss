# Native Android Kotlin Integration Guide

This guide covers the native Android/Kotlin backend integration for Bitcoin Core and Monero Wallet RPC.

## Overview

This implementation adds modular backend support to the AgoraDesk/LocalMonero Android app with:

- ✅ Bitcoin Core RPC integration
- ✅ Monero Wallet RPC integration  
- ✅ CoinGecko API for real-time exchange rates
- ✅ JWT-based authentication
- ✅ Encrypted local storage (EncryptedSharedPreferences)
- ✅ SSL certificate pinning
- ✅ ProGuard/R8 obfuscation for production
- ✅ Docker support for BTC/XMR nodes
- ✅ Material 3 UI components (via existing Flutter app)
- ✅ QR code scanning support
- ✅ Full test coverage infrastructure

## Prerequisites

### Required Software

1. **Android Studio**: Arctic Fox or newer
   - Download: https://developer.android.com/studio
   
2. **JDK 17**: 
   ```bash
   # Check version
   java -version
   ```

3. **Docker & Docker Compose** (for backend nodes):
   ```bash
   docker --version
   docker-compose --version
   ```

4. **Flutter SDK** (for existing Flutter components):
   ```bash
   flutter --version
   ```

5. **Gradle 7.4+** (included with Android Studio)

## Installation & Setup

### 1. Clone and Configure

```bash
# Clone the repository
git clone https://github.com/Decipheredmedia/agoradesk-app-foss.git
cd agoradesk-app-foss

# Copy environment template
cp .env.template .env

# Edit .env with your configuration
nano .env  # or use your preferred editor
```

### 2. Environment Configuration

Edit `.env` file with your actual values:

```bash
# Critical: Set strong passwords
BITCOIN_RPC_PASSWORD=your_strong_password_here
MONERO_RPC_PASSWORD=your_strong_password_here
JWT_SECRET_KEY=$(openssl rand -base64 64)

# API Keys
MAPBOX_API_KEY=your_mapbox_key_here
```

### 3. Start Backend Nodes (Docker)

#### Start All Services:
```bash
docker-compose up -d
```

#### Start Individual Services:
```bash
# Bitcoin only
docker-compose up -d bitcoin

# Monero only
docker-compose up -d monero-wallet monero-daemon
```

#### Check Service Status:
```bash
docker-compose ps
docker-compose logs -f bitcoin
docker-compose logs -f monero-wallet
```

### 4. Configure Android Build

#### Create Signing Keys:

```bash
# For AgoraDesk
keytool -genkey -v -keystore android/agoradesk-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias agoradesk

# For LocalMonero
keytool -genkey -v -keystore android/localmonero-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias localmonero
```

#### Create `android/key.properties`:

```properties
adStoreFile=../agoradesk-release-key.jks
adStorePassword=your_keystore_password
adKeyAlias=agoradesk
adKeyPassword=your_key_password

lmStoreFile=../localmonero-release-key.jks
lmStorePassword=your_keystore_password
lmKeyAlias=localmonero
lmKeyPassword=your_key_password
```

### 5. Create MapBox API Keys

```bash
# Copy template
cp lib/keys/keys.dart.template lib/keys/keys.dart

# Edit with your MapBox API key
nano lib/keys/keys.dart
```

Get your MapBox API key from: https://www.mapbox.com/

## Building the App

### Debug Build:

```bash
# Using Android Studio:
# 1. Open project in Android Studio
# 2. Select Build Variant: agoradeskDebug or localmoneroDebug
# 3. Click Run

# Using command line:
cd android
./gradlew assembleAgoradeskDebug
./gradlew assembleLocalmoneroDebug
```

### Release Build (FOSS):

```bash
# Build FOSS APKs (without Firebase)
./gradlew assembleAgoradeskRelease \
  -Dapp.flavor=agoradesk \
  -Dapp.includeFcm=false \
  -DMAPBOX_API_KEY="your_key"

./gradlew assembleLocalmoneroRelease \
  -Dapp.flavor=localmonero \
  -Dapp.includeFcm=false \
  -DMAPBOX_API_KEY="your_key"
```

### Release Build (Google Play):

```bash
# With Firebase Cloud Messaging
./gradlew bundleAgoradeskRelease \
  -Dapp.flavor=agoradesk \
  -DMAPBOX_API_KEY="your_key"

./gradlew bundleLocalmoneroRelease \
  -Dapp.flavor=localmonero \
  -DMAPBOX_API_KEY="your_key"
```

## Testing

### Run Unit Tests:

```bash
cd android
./gradlew test
./gradlew testAgoradeskDebugUnitTest
./gradlew testLocalmoneroDebugUnitTest
```

### Run Instrumentation Tests:

```bash
./gradlew connectedAndroidTest
```

### Linting and Static Analysis:

```bash
# Kotlin linting
./gradlew lint
./gradlew lintAgoradeskDebug

# Check for code issues
./gradlew check
```

## Backend Integration Usage

### Bitcoin RPC Client:

```kotlin
import com.agoradesk.app.backend.bitcoin.BitcoinRpcClient

val bitcoinClient = BitcoinRpcClient(
    rpcUrl = BuildConfig.BITCOIN_RPC_URL,
    rpcUser = secureStorage.getString(SecureStorage.KEY_BTC_RPC_USER) ?: "",
    rpcPassword = secureStorage.getString(SecureStorage.KEY_BTC_RPC_PASSWORD) ?: ""
)

// Get balance
val balance = bitcoinClient.getBalance()

// Get new address
val address = bitcoinClient.getNewAddress("my-label")

// Send transaction
val txid = bitcoinClient.sendToAddress("recipient_address", 0.001)

// List transactions
val transactions = bitcoinClient.listTransactions(10)
```

### Monero RPC Client:

```kotlin
import com.agoradesk.app.backend.monero.MoneroRpcClient

val moneroClient = MoneroRpcClient(
    rpcUrl = BuildConfig.MONERO_RPC_URL,
    rpcUser = secureStorage.getString(SecureStorage.KEY_XMR_RPC_USER),
    rpcPassword = secureStorage.getString(SecureStorage.KEY_XMR_RPC_PASSWORD)
)

// Get balance
val balance = moneroClient.getBalance()

// Get address
val address = moneroClient.getAddress()

// Transfer
val result = moneroClient.transfer("recipient_address", 1000000000) // amount in atomic units

// Get transfers
val transfers = moneroClient.getTransfers()
```

### CoinGecko Price API:

```kotlin
import com.agoradesk.app.backend.api.CoinGeckoApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val retrofit = Retrofit.Builder()
    .baseUrl(BuildConfig.COINGECKO_API_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val coinGeckoApi = retrofit.create(CoinGeckoApi::class.java)

// Get prices
val prices = coinGeckoApi.getPrice(
    ids = "bitcoin,monero",
    vsCurrencies = "usd,eur"
)
```

### Secure Storage:

```kotlin
import com.agoradesk.app.security.SecureStorage

val secureStorage = SecureStorage(context)

// Save credentials
secureStorage.saveString(SecureStorage.KEY_JWT_TOKEN, "token")
secureStorage.saveString(SecureStorage.KEY_BTC_RPC_USER, "user")
secureStorage.saveString(SecureStorage.KEY_BTC_RPC_PASSWORD, "password")

// Retrieve
val token = secureStorage.getString(SecureStorage.KEY_JWT_TOKEN)

// Clear
secureStorage.clear()
```

### JWT Authentication:

```kotlin
import com.agoradesk.app.security.JwtManager

val jwtManager = JwtManager(secretKey = "your_secret_key")

// Generate token
val token = jwtManager.generateToken("user123")

// Validate token
val isValid = jwtManager.validateToken(token)

// Get user ID from token
val userId = jwtManager.getUserId(token)
```

## Security Features

### 1. Encrypted Storage

All sensitive data (RPC credentials, JWT tokens, private keys) are stored using `EncryptedSharedPreferences` with AES-256 encryption.

### 2. SSL Certificate Pinning

Configured in `android/app/src/main/res/xml/network_security_config.xml`.

To add certificate pins:
```bash
# Get certificate hash
openssl s_client -connect agoradesk.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
```

### 3. ProGuard/R8 Obfuscation

Release builds automatically apply code obfuscation and shrinking via `proguard-rules.pro`.

### 4. Network Security

- Cleartext traffic disabled in production
- TLS 1.2+ required
- Debug overrides allow localhost for development

### 5. Anti-Debugging

ProGuard rules include anti-debugging measures:
- Code obfuscation
- Class name repackaging
- Logging removal in release builds

## Docker Management

### Start Services:
```bash
docker-compose up -d
```

### Stop Services:
```bash
docker-compose down
```

### View Logs:
```bash
docker-compose logs -f bitcoin
docker-compose logs -f monero-wallet
```

### Reset/Clean:
```bash
docker-compose down -v  # Removes volumes (WARNING: deletes blockchain data)
```

### Backup Blockchain Data:
```bash
# Bitcoin
docker cp bitcoin-node:/home/bitcoin/.bitcoin/wallet.dat ./backup/

# Monero
docker cp monero-wallet:/home/monero/.bitmonero/wallet ./backup/
```

## Troubleshooting

### Build Errors

**Error**: "Namespace not specified"
```bash
# Solution: Update build.gradle with namespace
android {
    namespace = "com.agoradesk.app"
}
```

**Error**: "Cannot find BuildConfig"
```bash
# Solution: Enable buildConfig in build.gradle
buildFeatures {
    buildConfig = true
}
```

### Runtime Errors

**Error**: "Unable to connect to RPC"
- Check Docker containers are running: `docker-compose ps`
- Verify RPC credentials in `.env`
- Check firewall rules

**Error**: "JWT validation failed"
- Verify JWT secret key is set correctly
- Check token expiration
- Ensure secret key matches between token generation and validation

### Docker Issues

**Error**: "Port already in use"
```bash
# Find process using port
lsof -i :8332
# Kill process or change port in docker-compose.yml
```

**Error**: "Container won't start"
```bash
# Check logs
docker-compose logs bitcoin
# Remove and recreate
docker-compose down
docker-compose up -d
```

## Production Deployment

### Pre-deployment Checklist:

- [ ] Update version in `pubspec.yaml`
- [ ] Set `DEBUG_LOGGING=false` in BuildConfig
- [ ] Configure SSL certificate pinning
- [ ] Test release build on physical device
- [ ] Run security scan: `./gradlew lint`
- [ ] Generate signed APK/AAB with release keystore
- [ ] Test on multiple Android versions (23-34)
- [ ] Backup signing keys securely
- [ ] Update ProGuard rules if needed

### Build for Google Play:

```bash
./gradlew bundleAgoradeskRelease
./gradlew bundleLocalmoneroRelease

# Bundles location:
# android/app/build/outputs/bundle/agoradeskRelease/
# android/app/build/outputs/bundle/localmoneroRelease/
```

### Build for F-Droid/Direct APK:

```bash
./gradlew assembleAgoradeskRelease -Dapp.includeFcm=false
./gradlew assembleLocalmoneroRelease -Dapp.includeFcm=false

# APKs location:
# android/app/build/outputs/apk/agoradesk/release/
# android/app/build/outputs/apk/localmonero/release/
```

## Continuous Integration

### GitHub Actions Example:

```yaml
name: Android CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Build with Gradle
        run: |
          cd android
          ./gradlew assembleDebug
          ./gradlew test
```

## Additional Resources

- [Android Developer Documentation](https://developer.android.com/)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Bitcoin Core RPC API](https://developer.bitcoin.org/reference/rpc/)
- [Monero RPC Documentation](https://www.getmonero.org/resources/developer-guides/wallet-rpc.html)
- [Material 3 Guidelines](https://m3.material.io/)
- [Retrofit Documentation](https://square.github.io/retrofit/)
- [OkHttp Documentation](https://square.github.io/okhttp/)

## Support

For issues or questions:
- Check existing GitHub issues
- Review the main README.md
- Consult PRODUCTION_DEPLOYMENT.md for deployment questions

## License

See LICENSE file in the repository root.
