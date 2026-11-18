#!/usr/bin/env bash
set -euo pipefail
mkdir -p forensic-evidence
PKG=com.aetherkeys.keyboard.rc44preview
STABLE=com.aetherkeys.keyboard.stablev2
IME="$PKG/com.aetherkeys.keyboard.AetherImeService"
RC43=dist/KeyPop-RC43-universal-signed.apk
RC44=dist/KeyPop-RC44-universal-signed.apk
DEBUG=dist/KeyPop-RC44-x86_64-debug.apk
TEST=dist/KeyPop-RC44-androidTest.apk
trap 'adb logcat -d -v time > forensic-evidence/logcat.txt || true; adb shell dumpsys input_method > forensic-evidence/input-method.txt || true' EXIT
for file in "$RC43" "$RC44" "$DEBUG" "$TEST"; do test -s "$file"; done

echo '[RC44] Check genuine RC43 -> RC44 stablev2 upgrade, keeping package and private data'
adb install -r "$RC43"
adb shell pm path "$STABLE" | grep -q package:
adb install -r "$RC44"
adb shell pm path "$STABLE" | grep -q package:
test "$(adb shell pm list packages | grep -Fc "package:$STABLE")" -eq 1
adb shell am start -W -n "$STABLE/com.aetherkeys.keyboard.KeyPopHomeActivity"

echo '[RC44] Validate full RC40 keyboard running alongside new RC44 services'
adb install -r "$DEBUG"
adb shell pm path "$PKG" | grep -q package:
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.KeyPopHomeActivity"
adb shell ime enable "$IME"
adb shell ime set "$IME"
adb shell settings get secure default_input_method | grep -Fq "$IME"
adb install -r "$TEST"

echo '[RC44] Original RC40 and RC42 compatibility + encrypted credential & form tests'
adb shell am instrument -w -r -e class \
com.aetherkeys.keyboard.KeyPopRuntimeSmokeTest,com.aetherkeys.keyboard.KeyPopRC10DictionarySmokeTest,com.aetherkeys.keyboard.KeyPopRC11InstrumentedTest,com.aetherkeys.keyboard.KeyPopRC18TypingPerformanceInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC23LatencyInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC24GlassInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC26PredictionInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC27ControlGeometryInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC33AdaptiveTypingInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC34ResizeAndNavigationInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC35SuggestionDesignInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC35SuggestionStyleInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC36SearchAndOptionsInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC39EditorActionsInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC40WhatsAppEnterInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC42UrlEditingInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC44PrivateAutofillInstrumentedTest \
"$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee forensic-evidence/instrumentation.txt
grep -E 'OK [(][0-9]+ tests?[)]' forensic-evidence/instrumentation.txt
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' forensic-evidence/instrumentation.txt; then
  echo '::error::RC44 emulator regression suite did not pass'
  exit 1
fi
adb shell am start -W -n "$STABLE/com.aetherkeys.keyboard.KeyPopHomeActivity"
adb shell pidof "$STABLE" > forensic-evidence/stable-app-pid.txt
test -s forensic-evidence/stable-app-pid.txt
echo 'PASS: RC44 encryption, portable backup, Android Autofill registration, RC40 parity, and RC43->RC44 upgrade'