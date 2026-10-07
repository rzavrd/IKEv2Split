# MYLO VPN — Android (IKEv2)

Android 11+ (API 30). applicationId `com.mylo.vpn`.

## Build a release APK
Android Studio: Build > Select Build Variant > `release`, then Build > Build APK(s).
Output: `app/build/outputs/apk/release/app-release.apk`
(Signing uses `keystore.properties` + `app/mylo-release.jks`. Keep both: every update must use the same key.)

If Gradle says "SDK location not found":
  echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties      (run inside this folder)

## Updates from your laptop
1. python3 tools/release.py setup --github OWNER/REPO   (or --base-url https://YOUR-DOMAIN/mylo)  -- BEFORE the first release build.
2. Send that first APK to people once. After that they update from inside the app.
3. New version:  python3 tools/release.py bump  ->  build release APK  ->  python3 tools/release.py publish ...
   (see the header of tools/release.py for --scp / --github / --base-url)
