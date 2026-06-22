# Flutter wrapper – keep all Flutter embedding classes.
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# App widget / JobIntentService host code.
-keep class com.agoradesk.app.** { *; }

# Kotlin coroutines – suppress noisy warnings about internal symbols.
-dontwarn kotlinx.coroutines.**

# Suppress warnings for optional dependencies that may not be present.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
