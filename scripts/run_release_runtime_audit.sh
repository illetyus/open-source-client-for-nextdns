#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
out="$PWD/build/compliance/runtime"
mkdir -p "$out"
tools="$ANDROID_HOME/build-tools/36.0.0"
sdk_manager=$(find "$ANDROID_HOME/cmdline-tools" -type f -path '*/bin/sdkmanager' | sort -V | tail -n 1)
test -x "$sdk_manager"
export PATH="$(dirname "$sdk_manager"):$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
key="$RUNNER_TEMP/release-runtime.p12"
export NEXTDNS_CI_AUDIT_PASSWORD
NEXTDNS_CI_AUDIT_PASSWORD="$(openssl rand -hex 32)"
echo "::add-mask::$NEXTDNS_CI_AUDIT_PASSWORD"
keytool -genkeypair -keystore "$key" -storetype PKCS12 -alias upload -keyalg RSA -keysize 3072 -validity 2 \
  -dname 'CN=Disposable CI release audit' -storepass:env NEXTDNS_CI_AUDIT_PASSWORD -keypass:env NEXTDNS_CI_AUDIT_PASSWORD -noprompt
"$tools/apksigner" sign --ks "$key" --ks-key-alias upload --ks-pass env:NEXTDNS_CI_AUDIT_PASSWORD \
  --key-pass env:NEXTDNS_CI_AUDIT_PASSWORD --out "$out/release-runtime.apk" app/build/outputs/apk/release/app-release-unsigned.apk
"$tools/apksigner" verify --verbose --print-certs "$out/release-runtime.apk" > "$out/signature.txt"
./gradlew :app:assembleReleaseAndroidTest -PreleaseRuntimeAudit=true --no-daemon
test_apk=$(find app/build/outputs/apk/androidTest/release -name '*.apk' -type f)
test -n "$test_apk"
"$tools/apksigner" sign --ks "$key" --ks-key-alias upload --ks-pass env:NEXTDNS_CI_AUDIT_PASSWORD \
  --key-pass env:NEXTDNS_CI_AUDIT_PASSWORD --out "$out/release-runtime-test.apk" "$test_apk"
unset NEXTDNS_CI_AUDIT_PASSWORD
# Android SDK licenses are already accepted on the hosted Android build runner.
"$sdk_manager" 'system-images;android-35;default;x86_64' 'emulator' 'platform-tools'
# Use one explicit AVD location for both command-line tools and the emulator.
# Hosted runners can give older SDK tools and newer emulators different defaults.
export ANDROID_USER_HOME="$RUNNER_TEMP/release-audit-android"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
mkdir -p "$ANDROID_AVD_HOME"
printf 'no\n' | avdmanager create avd -n nextdns-release-audit -k 'system-images;android-35;default;x86_64' \
  --path "$ANDROID_AVD_HOME/nextdns-release-audit.avd" --force
test -f "$ANDROID_AVD_HOME/nextdns-release-audit.ini"
"$ANDROID_HOME/emulator/emulator" -list-avds | grep -Fx 'nextdns-release-audit'
sudo chmod 666 /dev/kvm
"$ANDROID_HOME/emulator/emulator" -avd nextdns-release-audit -no-window -no-audio -no-boot-anim \
  -no-snapshot -gpu swiftshader_indirect -memory 2048 > "$out/emulator.log" 2>&1 &
emulator_pid=$!
trap 'kill "$emulator_pid" 2>/dev/null || true' EXIT
timeout 180 adb wait-for-device
for attempt in $(seq 1 120); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = '1' ]; then break; fi
  sleep 2
done
test "$(adb shell getprop sys.boot_completed | tr -d '\r')" = '1'
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r "$out/release-runtime.apk"
adb install -r "$out/release-runtime-test.apk"
adb logcat -c
timeout 240 adb shell am instrument -w -r -e class com.example.ReleaseSecurityRuntimeTest,com.example.WorldMapStrictModeTest \
  com.aistudio.nextdns.mgrqvt.test/androidx.test.runner.AndroidJUnitRunner > "$out/instrumentation.txt"
adb logcat -d -v threadtime > "$out/logcat.txt"
python3 scripts/verify_release_runtime.py


