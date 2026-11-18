#!/usr/bin/env bash
# Single shell is mandatory: the emulator runner invokes script lines separately.
set -euo pipefail

PKG="com.aetherkeys.keyboard.rc42preview"
IME="$PKG/com.aetherkeys.keyboard.AetherImeService"
APP="dist/KeyPop-RC42-x86_64-debug.apk"
TEST="dist/KeyPop-RC42-androidTest.apk"
test -s "$APP"
test -s "$TEST"
echo "[RC42] Install keyboard and instrumentation APK"
adb install -r "$APP"
adb shell pm path "$PKG" | grep -q package:

echo "[RC42] Start host Activity and select actual keyboard"
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.MainActivity"
adb shell ime enable "$IME"
adb shell ime set "$IME"
adb shell settings get secure default_input_method | grep -F "$IME"

echo "[RC42] Run *all* Android instrumentation tests (URL editor, selection, etc.)"
adb install -r "$TEST"
adb shell am instrument -w -r "$PKG.test/androidx.test.runner.AndroidJUnitRunner" \
    | tee instrumentation.txt
grep -E 'OK [(][0-9]+ tests?[)]' instrumentation.txt
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' instrumentation.txt; then
    echo "Instrumentation failed; retaining detailed report" >&2
    exit 1
fi
echo "[RC42] PASS: keyboard launched and instrumentation succeeded"
