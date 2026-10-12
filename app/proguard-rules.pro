<<<<<<< HEAD
# Compose / coroutines work with the default rules.
-dontwarn org.jetbrains.annotations.**

# Android 11/12 phones do not have the Android 12+ IKEv2 classes. R8 must not inline ModernProfile into
# code that also runs on Android 11, or the app can crash when the VPN is switched on.
-dontoptimize
-keep class com.ikev2split.app.vpn.ModernProfile { *; }
-keep class com.ikev2split.app.vpn.LegacyProfile { *; }
-dontwarn android.net.**
=======
# Compose / coroutines work with the default optimize rules. Nothing special to keep.
-dontwarn org.jetbrains.annotations.**
>>>>>>> origin/main
