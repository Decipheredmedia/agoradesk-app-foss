# Flutter wrapper – keep all Flutter embedding classes.
-keep class io.flutter.** { *; }
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.plugins.** { *; }
-keep class io.flutter.embedding.** { *; }

# Firebase – keep classes used via reflection by the Firebase SDKs.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# App widget / JobIntentService host code.
-keep class com.agoradesk.app.** { *; }

# Kotlin coroutines – suppress noisy warnings about internal symbols.
-dontwarn kotlinx.coroutines.**

# Suppress warnings for optional dependencies that may not be present.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
