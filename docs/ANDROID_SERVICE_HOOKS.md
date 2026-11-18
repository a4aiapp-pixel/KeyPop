# RC42 integration into KeyPop's existing Android IME

**Do not publish an APK as "RC42 Personal Intelligence" until every gate below passes.** The ZIP contains isolated implementation classes. It does **not** modify `AetherImeService.kt`, `MainActivity.kt`, app version, package identity, or the existing RC40 workflow because those files were not available through the disconnected GitHub integration.

## 1. Service-owned lifecycle

- Inspect the latest `AetherImeService.kt` in the repository before merging; RC40 used `requestSuggestionRefresh()`, `refreshSuggestions()`, `updateSuggestionVisibility()`, the existing three-chip `suggestionBar`, `onStartInputView`, `onUpdateSelection`, `onFinishInputView`, and a central `press(value)` event handler.
- Create one `KeyPopAdaptiveServiceController(applicationContext)` while the IME service is active. Close it in `onDestroy()`.
- Call `invalidateSession()` whenever the keyboard switches editor/package, caret range, selection, input type, or when the view finishes. Also invalidate before any long-running translator or composing action that changes editor text.
- **Never** issue `getTextBeforeCursor()` or `getTextAfterCursor()` in password/OTP/financial/sensitive fields. `KeyPopAdaptiveServiceController` also checks EditorInfo policy, but this is a second defense, not permission to read secure text first.
- Limit the context snapshot to about 220 prior characters and 48 following characters, and never include selection text or clipboard content in phrase learning.

## 2. One unified candidate ranking pass

Hook the response of `controller.predict(before, after, info, incognito) { adaptive -> ... }` into the **existing** suggestion strip. Combine with current lexicon, correction, grammar and personal dictionary candidates through `KeyPopAdaptiveCandidatePlanner.combine(adaptive, existingCandidates, 3)`. Assign calibrated scores to existing candidates, preferring a contextual multiword phrase over a generic unrelated dictionary word, but never suppressing a better legitimate correction. Keep the strip present in Chrome search editors when safe; do not make a second invisible prediction strip.

The controller callback runs on the main thread and discards stale requests internally. **Still** recheck that the input package, cursor/selection and immediate editor context match the snapshot before displaying or accepting a suggestion. A recently typed address must not appear in a password field, a different application or incognito mode.

## 3. Tapped suggestions

For every tapped candidate, call `KeyPopAdaptiveCandidatePlanner.makePlan(beforeCursor, afterCursor, candidate.replacement)` (not `display`). The plan returns `replaceBeforeChars` and `insert`. Reject the tap if the editor context/selection has changed. When permitted, use the *same current* InputConnection and `beginBatchEdit()`, `deleteSurroundingText(plan.replaceBeforeChars, 0)`, `commitText(plan.insert, 1)`, `endBatchEdit()`. Refresh suggestions only when the host reports success and do not insert duplicated spaces. Maintain the existing undo behavior and avoid committing phrases inside a token. For emoji/RTL/surrogate boundaries, verify replacement length against UTF-16 cursor offsets; do not assume one code point equals one UTF-16 char.

## 4. Learning controls and consent

In KeyPop's settings navigation, expose `KeyPopAdaptiveSettingsDialog.show(activity, erase = ...)` with actual wiring to the running service controller. Controls must default OFF:

- `adaptive_personal_words`: learning from user-accepted words only.
- `adaptive_finished_phrases`: learning from explicitly confirmed/finalized drafts, **not** every keystroke, incoming WhatsApp message, clipboard paste, or displayed source text.
- `adaptive_addresses`: separately authorizes private address phrase memory.
- `adaptive_excluded_apps`: configurable application package exclusion list (must have a visible per-app UI).

`controller.learnAcceptedWord()` and `controller.learnConfirmedPhrase()` must be called only after consent, safe-editor gating and an actual accepted/completed action. Do not learn from URLs, email, payment/OTP, password fields, incognito, or restricted apps. `eraseAll()` must clear the in-memory model and encrypted store while the IME is running; deleting the file from a separate Activity alone is insufficient if the IME later writes its memory back.

The `KeyPopAdaptiveHistoryStore` saves local AES-GCM ciphertext under Android Keystore. If it cannot decrypt an existing file, preserve it and provide a user-facing reset prompt rather than silently overwriting it.

## 5. Multilingual and neural models

The bundled candidate model offers simple English grammar rules, personal phrases, and generic Unicode tokenization; it is **not** a multilingual neural model and does not reach Gboard parity. Use script-aware routing so English heuristics do not override Urdu/Sindhi native-script candidates. Do not label Urdu/Sindhi performance as independently validated. A neural ranker can be an optional future module after licensing, model size, offline latency, battery, and accuracy evaluation; this package does not ship model weights or an inference runtime.

## 6. URL field integrity and full editing reliability

The user also reported URL truncation and inability to edit URL text. Review `docs/URL_EDITING_REGRESSIONS.md`. For explicit URI/email fields and URL-like tokens in a generic search editor, run `KeyPopUrlEditingPolicy.decide()` before auto-spacing, punctuation, candidate rendering, token replacement or local learning. This policy is also enforced in the adaptive service controller, candidate planner and input field policy. The real `AetherImeService.kt` must consume this decision on each keypress. Test long URL preservation, editing at middle-of-URL and selection deletion in Chrome and other apps. A visually ellipsized Chrome URL is not proof of actual text truncation.

## 7. Build, privacy and runtime acceptance gates

Run `bash verification/run_local_checks.sh` on a host with Kotlin, then `gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` in the actual checkout. Add instrumentation that asserts suggestions are VISIBLE in the actual KeyPop suggestion strip and can be accepted in an Android EditText, including: `You a` -> `are`, `you are allow` -> `allowed`, explicitly learned `house no` -> the rest of the address, and a sensitive field with no personal candidates. Test WhatsApp, WhatsApp Business, Chrome and multiline/selection behavior manually because emulator EditText alone cannot reproduce vendor-host code.

Before publishing, fix a **stable** release signature and choose a clean RC42 debug identity, then test `adb install -r`, package coexistence, IME switcher, Android 26/35, ARM64 and Universal variants. RC39/RC40 debug previews were temporarily signed; do not claim that a differently signed build can replace them in-place. Keep RC40/RC39 verified release tags intact.

An APK that compiles without displaying adaptive suggestions in real app text fields is NOT a complete RC42 release.
