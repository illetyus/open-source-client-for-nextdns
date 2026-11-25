#!/usr/bin/env bash
set -euo pipefail

out="${1:?runtime evidence directory is required}"
runner='com.aistudio.nextdns.mgrqvt.test/androidx.test.runner.AndroidJUnitRunner'

adb -e install "$out/beta-re-signed.apk"
adb -e install "$out/release-runtime-test.apk"
timeout 120 adb -e shell am instrument -w -r -e class \
  'com.example.NativeBetaUpgradeTest#seedSettingsInPublishedBeta' "$runner" > "$out/beta-seed.txt"
grep -Fq 'OK (1 test)' "$out/beta-seed.txt"
adb -e shell am force-stop com.aistudio.nextdns.mgrqvt
timeout 120 adb -e shell am instrument -w -r -e class \
  'com.example.NativeBetaUpgradeTest#publishedBetaReadsBackSeededSettings' "$runner" > "$out/beta-readback.txt"
grep -Fq 'OK (1 test)' "$out/beta-readback.txt"

adb -e install -r "$out/release-runtime.apk" | tee "$out/beta-upgrade-install.txt"
grep -Fxq 'Success' "$out/beta-upgrade-install.txt"
timeout 120 adb -e shell am instrument -w -r -e class \
  'com.example.NativeBetaUpgradeTest#installedUpgradePreservesBetaSettings' "$runner" > "$out/beta-upgrade.txt"
grep -Fq 'OK (1 test)' "$out/beta-upgrade.txt"

# The remaining release audit starts with a clean application on this emulator.
test "$(adb -e shell pm clear com.aistudio.nextdns.mgrqvt | tr -d '\r')" = 'Success'
