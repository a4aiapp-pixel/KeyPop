#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v kotlinc >/dev/null || { echo 'kotlinc not found (requires Kotlin compiler)' >&2; exit 2; }
command -v java >/dev/null || { echo 'java not found' >&2; exit 2; }
JAR="$(mktemp "${TMPDIR:-/tmp}/keypop-rc42-XXXXXX.jar")"
trap 'rm -f "$JAR"' EXIT
kotlinc \
  "$ROOT/app/src/main/java/com/aetherkeys/keyboard/KeyPopAdaptiveLanguageModel.kt" \
  "$ROOT/app/src/main/java/com/aetherkeys/keyboard/KeyPopAdaptiveCandidatePlanner.kt" \
  "$ROOT/app/src/main/java/com/aetherkeys/keyboard/KeyPopAdaptiveEditorPolicy.kt" \
  "$ROOT/app/src/main/java/com/aetherkeys/keyboard/KeyPopUrlEditingPolicy.kt" \
  "$ROOT/verification/RunRc42Regression.kt" \
  -include-runtime -d "$JAR"
java -jar "$JAR"
