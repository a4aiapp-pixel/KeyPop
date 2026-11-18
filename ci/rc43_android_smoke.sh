#!/usr/bin/env bash
set -euo pipefail
mkdir -p forensic-evidence
PKG="com.aetherkeys.keyboard.rc43preview"
STABLE="com.aetherkeys.keyboard.stablev2"
IME="$PKG/com.aetherkeys.keyboard.AetherImeService"
DEBUG="dist/KeyPop-RC43-x86_64-debug.apk"
TEST="dist/KeyPop-RC43-androidTest.apk"
RC42="dist/KeyPop-RC42-universal-signed.apk"
RC43="dist/KeyPop-RC43-universal-signed.apk"
trap 'adb logcat -d -v time > forensic-evidence/logcat.txt || true; adb shell dumpsys input_method > forensic-evidence/ime-state.txt || true' EXIT
for x in "$DEBUG" "$TEST" "$RC42" "$RC43"; do test -s "$x"; done

echo "[RC43] Verify REAL signed RC42 to RC43 in-place upgrade"
adb install -r "$RC42"
adb shell pm path "$STABLE" | grep -q package:
adb install -r "$RC43"
adb shell pm path "$STABLE" | grep -q package:
test "$(adb shell pm list packages | grep -Fc "package:$STABLE")" -eq 1

echo "[RC43] Verify full-featured RC40+RC42 debug IME installation"
adb install -r "$DEBUG"
adb shell pm path "$PKG" | grep -q package:
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.KeyPopHomeActivity"
adb shell ime enable "$IME"
adb shell ime set "$IME"
adb shell settings get secure default_input_method | grep -F "$IME"
adb install -r "$TEST"

echo "[RC43] Run RC40 regression suites and RC42 URL/selection tests"
adb shell am instrument -w -r -e class \
com.aetherkeys.keyboard.KeyPopRuntimeSmokeTest,com.aetherkeys.keyboard.KeyPopRC10DictionarySmokeTest,com.aetherkeys.keyboard.KeyPopRC11InstrumentedTest,com.aetherkeys.keyboard.KeyPopRC18TypingPerformanceInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC23LatencyInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC24GlassInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC26PredictionInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC27ControlGeometryInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC33AdaptiveTypingInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC34ResizeAndNavigationInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC35SuggestionDesignInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC35SuggestionStyleInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC36SearchAndOptionsInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC39EditorActionsInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC40WhatsAppEnterInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC42UrlEditingInstrumentedTest \
"$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee forensic-evidence/instrumentation.txt
grep -E 'OK [(][0-9]+ tests?[)]' forensic-evidence/instrumentation.txt
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' forensic-evidence/instrumentation.txt; then
  echo "::error::RC43 instrumental regression failed"
  exit 1
fi
adb shell am start -W -n "$STABLE/com.aetherkeys.keyboard.KeyPopHomeActivity"
adb shell pidof "$STABLE" > forensic-evidence/stable-pid.txt
test -s forensic-evidence/stable-pid.txt
echo "PASS RC43: real RC42 -> RC43 in-place upgrade, IME launch and regression suites"
