# Troubleshooting Guide

This guide helps you resolve common issues when building and running the AgoraDesk/LocalMonero Android app.

## Table of Contents

1. [Build Errors](#build-errors)
2. [Runtime Errors](#runtime-errors)
3. [Docker Issues](#docker-issues)
4. [Network Connectivity](#network-connectivity)
5. [Security and Authentication](#security-and-authentication)
6. [Performance Issues](#performance-issues)

## Build Errors

### Error: "Namespace not specified"

**Problem**: Build fails with "Namespace not specified" error.

**Solution**:
```gradle
// In android/app/build.gradle, ensure namespace is set:
android {
    namespace = "com.agoradesk.app"
    // ... rest of config
}
```

### Error: "Cannot find BuildConfig"

**Problem**: Code references `BuildConfig` but it's not found.

**Solution**:
```gradle
// In android/app/build.gradle, enable buildConfig:
android {
    buildFeatures {
        buildConfig = true
    }
}
```

### Error: "Execution failed for task ':app:lintVitalRelease'"

**Problem**: Lint errors blocking release build.

**Solution**:
```gradle
// Disable lint blocking in android/app/build.gradle:
android {
    lintOptions {
        checkReleaseBuilds false
        abortOnError false
    }
}
```

### Error: "Keystore not found"

**Problem**: Release build fails due to missing keystore.

**Solution**:
1. Create keystore:
   ```bash
   keytool -genkey -v -keystore agoradesk-release-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias agoradesk
   ```

2. Update `android/key.properties`:
   ```properties
   adStoreFile=../agoradesk-release-key.jks
   adStorePassword=your_password
   adKeyAlias=agoradesk
   adKeyPassword=your_password
   ```

### Error: "Could not resolve dependencies"

**Problem**: Gradle cannot download dependencies.

**Solution**:
```bash
# Clean and rebuild
cd android
./gradlew clean
./gradlew build --refresh-dependencies

# If still failing, clear Gradle cache:
rm -rf ~/.gradle/caches
```

### Error: "MapBox API key not found"

**Problem**: Build fails due to missing MapBox key.

**Solution**:
1. Create `lib/keys/keys.dart`:
   ```dart
   const String keysMapToken = 'your_mapbox_api_key';
   ```

2. Or pass via environment:
   ```bash
   flutter build apk --dart-define=MAPBOX_API_KEY=your_key
   ```

### Error: "Kotlin version mismatch"

**Problem**: Different Kotlin versions causing conflicts.

**Solution**:
```gradle
// In android/build.gradle, ensure consistent version:
buildscript {
    ext.kotlin_version = '1.9.20'
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}
```

## Runtime Errors

### Error: "Unable to connect to RPC"

**Problem**: App cannot connect to Bitcoin or Monero RPC.

**Solutions**:
1. **Check Docker containers**:
   ```bash
   docker-compose ps
   docker-compose logs bitcoin
   docker-compose logs monero-wallet
   ```

2. **Verify RPC configuration**:
   - Check `.env` file has correct URLs and credentials
   - Default Bitcoin: `http://localhost:8332`
   - Default Monero: `http://localhost:18082`

3. **For Android Emulator**:
   Use `10.0.2.2` instead of `localhost`:
   ```bash
   BITCOIN_RPC_URL=http://10.0.2.2:8332
   MONERO_RPC_URL=http://10.0.2.2:18082
   ```

4. **Check firewall**:
   ```bash
   # Allow RPC ports
   sudo ufw allow 8332/tcp
   sudo ufw allow 18082/tcp
   ```

### Error: "JWT validation failed"

**Problem**: Authentication fails with JWT errors.

**Solutions**:
1. **Check JWT secret**:
   - Ensure JWT_SECRET_KEY is set in `.env`
   - Secret must be at least 32 characters

2. **Generate new secret**:
   ```bash
   openssl rand -base64 64
   ```

3. **Clear stored tokens**:
   ```kotlin
   val secureStorage = SecureStorage(context)
   secureStorage.clear()
   ```

### Error: "Encrypted storage initialization failed"

**Problem**: EncryptedSharedPreferences fails to initialize.

**Solutions**:
1. **Clear app data**:
   ```bash
   adb shell pm clear com.agoradesk.app
   ```

2. **Reinstall app**:
   ```bash
   adb uninstall com.agoradesk.app
   ./gradlew installAgoradeskDebug
   ```

3. **Check API level**:
   - EncryptedSharedPreferences requires API 23+

### Error: "Network Security Exception"

**Problem**: HTTPS requests failing due to certificate pinning.

**Solutions**:
1. **For development**, add to `network_security_config.xml`:
   ```xml
   <debug-overrides>
       <domain-config cleartextTrafficPermitted="true">
           <domain includeSubdomains="true">localhost</domain>
       </domain-config>
   </debug-overrides>
   ```

2. **For production**, update certificate pins:
   ```bash
   # Get certificate hash
   openssl s_client -connect agoradesk.com:443 | \
     openssl x509 -pubkey -noout | \
     openssl pkey -pubin -outform der | \
     openssl dgst -sha256 -binary | base64
   ```

### Error: "QR Scanner not working"

**Problem**: QR code scanning fails or crashes.

**Solutions**:
1. **Check camera permissions**:
   ```xml
   <!-- In AndroidManifest.xml -->
   <uses-permission android:name="android.permission.CAMERA" />
   ```

2. **Request permission at runtime**:
   ```kotlin
   if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
       != PackageManager.PERMISSION_GRANTED) {
       ActivityCompat.requestPermissions(activity,
           arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
   }
   ```

## Docker Issues

### Error: "Port already in use"

**Problem**: Docker cannot bind to port 8332 or 18082.

**Solutions**:
1. **Find process using port**:
   ```bash
   lsof -i :8332
   lsof -i :18082
   ```

2. **Kill process**:
   ```bash
   kill -9 <PID>
   ```

3. **Change port in docker-compose.yml**:
   ```yaml
   services:
     bitcoin:
       ports:
         - "18332:8332"  # Use different host port
   ```

### Error: "Container won't start"

**Problem**: Bitcoin or Monero container fails to start.

**Solutions**:
1. **Check logs**:
   ```bash
   docker-compose logs bitcoin
   docker-compose logs monero-wallet
   ```

2. **Verify configuration**:
   ```bash
   cat docker/bitcoin/bitcoin.conf
   cat docker/monero/monero-wallet.conf
   ```

3. **Remove and recreate**:
   ```bash
   docker-compose down
   docker-compose up -d --force-recreate
   ```

### Error: "Out of disk space"

**Problem**: Blockchain data fills up disk.

**Solutions**:
1. **Check disk usage**:
   ```bash
   docker system df
   ```

2. **Prune old data**:
   ```bash
   docker system prune -a --volumes
   ```

3. **For Bitcoin**, enable pruning in `bitcoin.conf`:
   ```conf
   prune=550  # Keep only 550MB of blocks
   ```

### Error: "Slow blockchain sync"

**Problem**: Initial blockchain download is very slow.

**Solutions**:
1. **Use pruned mode**:
   - Bitcoin: Set `prune=550` in bitcoin.conf
   - Monero: Use `--prune-blockchain` flag

2. **Increase peers**:
   ```conf
   maxconnections=125  # In bitcoin.conf
   ```

3. **Use bootstrap**:
   - Download blockchain snapshot
   - Mount as volume in docker-compose.yml

## Network Connectivity

### Error: "Connection timeout"

**Problem**: RPC calls timeout.

**Solutions**:
1. **Increase timeout**:
   ```kotlin
   val client = OkHttpClient.Builder()
       .connectTimeout(60, TimeUnit.SECONDS)
       .readTimeout(60, TimeUnit.SECONDS)
       .build()
   ```

2. **Check network**:
   ```bash
   # Test connectivity
   curl http://localhost:8332
   telnet localhost 8332
   ```

### Error: "SSL Handshake failed"

**Problem**: HTTPS connection fails.

**Solutions**:
1. **Verify certificate**:
   ```bash
   openssl s_client -connect agoradesk.com:443
   ```

2. **Update trust store**:
   - Ensure Android system certificates are up to date

3. **Disable SSL pinning for testing** (debug only):
   ```xml
   <debug-overrides>
       <trust-anchors>
           <certificates src="user" />
           <certificates src="system" />
       </trust-anchors>
   </debug-overrides>
   ```

## Security and Authentication

### Error: "Invalid JWT signature"

**Problem**: JWT signature validation fails.

**Solutions**:
1. **Check secret key consistency**:
   - Same key used for signing and verification
   - Key is properly encoded

2. **Regenerate token**:
   ```kotlin
   val newToken = jwtManager.generateToken(userId)
   secureStorage.saveString(SecureStorage.KEY_JWT_TOKEN, newToken)
   ```

### Error: "RPC authentication failed"

**Problem**: Cannot authenticate to Bitcoin/Monero RPC.

**Solutions**:
1. **Verify credentials**:
   ```bash
   # Test Bitcoin RPC
   curl --user bitcoinrpc:password \
     --data-binary '{"jsonrpc":"1.0","id":"test","method":"getblockchaininfo","params":[]}' \
     http://localhost:8332
   ```

2. **Update credentials**:
   - Edit `docker/bitcoin/bitcoin.conf`
   - Restart container: `docker-compose restart bitcoin`

3. **Check configuration file**:
   ```bash
   docker exec bitcoin-node cat /home/bitcoin/.bitcoin/bitcoin.conf
   ```

## Performance Issues

### Problem: "Slow app startup"

**Solutions**:
1. **Enable ProGuard/R8** for release builds
2. **Reduce dependencies**:
   ```gradle
   implementation "androidx.startup:startup-runtime:1.1.1"
   ```
3. **Use lazy initialization**:
   ```kotlin
   private val heavyObject by lazy { HeavyObject() }
   ```

### Problem: "High memory usage"

**Solutions**:
1. **Enable memory profiling**:
   ```bash
   adb shell dumpsys meminfo com.agoradesk.app
   ```

2. **Use LeakCanary** (debug builds):
   ```gradle
   debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.12'
   ```

3. **Optimize bitmap loading**:
   - Use proper image sizes
   - Enable hardware acceleration

### Problem: "Slow RPC calls"

**Solutions**:
1. **Use connection pooling**:
   ```kotlin
   val client = OkHttpClient.Builder()
       .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
       .build()
   ```

2. **Enable HTTP/2**:
   - Automatically enabled in OkHttp 3+

3. **Cache responses**:
   ```kotlin
   val cacheSize = 10 * 1024 * 1024 // 10 MB
   val cache = Cache(context.cacheDir, cacheSize.toLong())
   val client = OkHttpClient.Builder()
       .cache(cache)
       .build()
   ```

## Getting Help

If you're still experiencing issues:

1. **Check GitHub Issues**: https://github.com/Decipheredmedia/agoradesk-app-foss/issues
2. **Review Documentation**:
   - [ANDROID_NATIVE_INTEGRATION.md](ANDROID_NATIVE_INTEGRATION.md)
   - [PRODUCTION_DEPLOYMENT.md](PRODUCTION_DEPLOYMENT.md)
3. **Enable debug logging**:
   ```kotlin
   BuildConfig.DEBUG_LOGGING = true
   ```
4. **Collect logs**:
   ```bash
   adb logcat > app_logs.txt
   ```

## Common Solutions Summary

| Issue | Quick Fix |
|-------|-----------|
| Build fails | `./gradlew clean build` |
| RPC timeout | Check Docker: `docker-compose ps` |
| Auth fails | Clear storage and regenerate tokens |
| Port in use | Change ports in docker-compose.yml |
| Slow sync | Enable blockchain pruning |
| Memory leak | Use LeakCanary to identify |
| Network error | Check network_security_config.xml |
