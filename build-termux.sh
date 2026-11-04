#!/data/data/com.termux/files/usr/bin/bash
# KIARA AI - Termux build helper (best effort, untested on your device).
# Usage: cd KiaraAI && bash build-termux.sh
set -e

echo "==> Installing packages (JDK 17, Gradle, aapt2, git)..."
pkg update -y
pkg install -y openjdk-17 gradle aapt2 git

# The Android SDK must be an aarch64 build (official Google SDK tools are x86 and will NOT run in Termux).
# Put your SDK anywhere and export ANDROID_HOME, e.g. in ~/.bashrc:
#   export ANDROID_HOME=$HOME/android-sdk
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
if [ ! -f "$SDK/platforms/android-34/android.jar" ]; then
  echo ""
  echo "ERROR: Android SDK platform 34 not found at: $SDK"
  echo "You need a Termux-compatible (aarch64) Android SDK containing:"
  echo "   $SDK/platforms/android-34/android.jar"
  echo "Set ANDROID_HOME to it and re-run. If you can't get one, use the GitHub Actions"
  echo "build instead (.github/workflows/build.yml) - see README.md, Option A."
  exit 1
fi

echo "sdk.dir=$SDK" > local.properties
export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-17-openjdk}"

echo "==> Building debug APK (this can take 10-30 minutes the first time)..."
gradle assembleDebug --no-daemon \
  -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2" \
  -Dorg.gradle.jvmargs="-Xmx1536m"

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo "==> Done: $APK"
  if [ -d "$HOME/storage/downloads" ]; then
    cp "$APK" "$HOME/storage/downloads/KiaraAI.apk"
    echo "Copied to Downloads/KiaraAI.apk"
  else
    echo "Tip: run 'termux-setup-storage' to copy the APK to Downloads automatically."
  fi
  termux-open "$APK" || true
else
  echo "Build finished but APK not found. Check the Gradle output above."
  exit 1
fi
