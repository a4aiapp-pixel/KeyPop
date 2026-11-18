# KeyPop RC41 – Adaptive language engine integration (offline patch)

**Status:** Portable implementation with JVM regression tests. **Not yet merged into the KeyPop GitHub repository, not an APK.** The ChatGPT GitHub connector currently rejects repository operations (`link_id must identify an eligible linked account`), so the existing keyboard service and its build cannot be modified or verified from this session.

## Features implemented in these source files

- Context-sensitive suggestions and grammar stems: `You a` → `are`, `you are allow` → `allowed`, `I a` → `am`.
- Opt-in local phrase memory, with word and multi-word completions matching the previous one to five words. Example: after a user previously typed `house no 169, street no 9, J Sector`, entering `house no ` offers `169, street no 9, J Sector`; entering `house no` also offers `no 169, street no 9, J Sector` as a replacement for the current token.
- Recency and frequency ranking, capped at 400 phrase records plus 1,500 personal words, maximum eight following tokens per completion. Exact phrase memory does not pretend to be a neural/Gboard model.
- Sensitive-field exclusion and local-learning consent. The API refuses to learn without an explicit enabling flag, refuses passwords/verification codes, URLs, email addresses and card-like numbers, and has a per-phrase deletion/erase method.
- Optional AES-256-GCM local encrypted persistence using Android Keystore, atomic writes, bounded reads and explicit error signalling. Runs on a background executor only.

## Required integration into existing RC40 keyboard (must be performed in repository)

These are **integration instructions**, not changes already applied to RC40:

1. Add the two `.kt` files in `app/src/main/java/com/aetherkeys/keyboard/` and the unit test in `app/src/test/java/com/aetherkeys/keyboard/`.
2. Add Settings controls: **Personal word and phrase suggestions** (default OFF, explicit opt-in), **Learn my finished phrases** (separate opt-in or combined with clear explanation), **Erase learned phrases**, and **Exclude app**. Explain that learned text may include personal addresses; all processing is on-device, and no learning from password/incognito/payment fields is allowed.
3. Add `KeyPopAdaptiveLanguageModel` as a service-owned object and load/snapshot using `KeyPopAdaptiveHistoryStore` *on the existing background prediction executor*: use `val saved = store.load(); model.restore(saved.phrases); model.restoreWords(saved.words)` and save with `store.save(model.snapshot(), model.wordSnapshot())`. Guard asynchronous results with the current input-session ID because predictions cannot be applied to a different app or editor.
4. In `AetherImeService.renderSuggestions()` (called by `requestSuggestionRefresh()`), call `model.predict(beforeCursor, afterCursor, enabled, sensitive, 3)` as part of the **same** candidate ranking pass as existing `KeyPopPredictionContext`, lexicon and `KeyPopPersonalLanguage`. Merge by normalized replacement, retaining the highest score; give short context-aware grammar and learned phrase candidates enough priority to be visible in the three suggestion chips. Avoid using a separate strip that might be hidden. Long phrase `display` is truncated; `replacement` is the full text.
5. In the suggestion tap handler (`replaceWord()`), replace **only the current token**, or insert if at an actual word boundary. A phrase candidate's `replacement` starts with the already typed partial token; never duplicate it. Do not force a trailing space on an insertion whose continuation already includes spaces. Check that the host `InputConnection` actually committed the text before setting prediction state.
6. After a user accepts a suggestion or completes a word, call `model.learnWord(word, enabled, sensitiveEditor)` only if explicitly opted in. Learn a completed phrase only from user-confirmed non-sensitive input: detect a genuine finalized editor draft (e.g. app submission/reset, or explicit keyboard action) rather than saving every keystroke. Keep the current editor session's draft local until it is finalized; do not retain unsent or incognito text. Call `learnPhrase(text, enabled, sensitiveEditor)` and schedule `store.save(model.snapshot(), model.wordSnapshot())` on the existing worker executor, never the keyboard UI thread. Keep a per-app exclusion preference. Never learn from clipboard paste, password/OTP fields, or a URL/Chrome address editor.
7. Add a Settings action that calls `model.clear()` and `store.erase()` on a worker thread. If encrypted loading fails, explain that existing memory could not be read, rather than silently overwriting it.
8. Keep the **same stable Android package ID and long-lived release signing certificate** for future *production-signed* updates. RC40 used a debug preview identity and temporary signing; RC41 debug APK cannot safely overwrite it. Explicitly test upgrade installs with `adb install -r` for future signed builds.
9. Add a CI emulator test using a real `EditText` and service suggestion strip to verify user-facing `You a`, `you are allow`, and a previously learned `house no ...` phrase. The pure JVM tests alone do not prove that the visible chips work in Chrome or WhatsApp.

### Integration helper example (not a verbatim patch against unavailable RC40 source)

```kotlin
// After you have read the current InputConnection context and gated sensitive input:
val local = adaptiveModel.predict(
    beforeCursor = before,
    afterCursor = after,
    personalLearningEnabled = prefs.getBoolean("adaptivePersonalLearning", false),
    sensitiveEditor = sensitive(currentInputEditorInfo),
    maxCandidates = 3
)
// Merge local with lexicon/grammar offers, then render in existing three chips.
// For any tapped candidate use candidate.replacement, not candidate.display.
```

## Validation

The `verification/RunRegression.kt` standalone harness requires only Kotlin/JVM. Run:

```bash
kotlinc app/src/main/java/com/aetherkeys/keyboard/KeyPopAdaptiveLanguageModel.kt \
  verification/RunRegression.kt -include-runtime -d regression.jar
java -jar regression.jar
```

The `KeyPopAdaptiveLanguageModelTest.kt` file is an ordinary JUnit4 test compatible with RC40's existing `testImplementation("junit:junit:4.13.2")`.

**Privacy and capability note:** The test phrases in this patch are examples, not text collected from the user's device. The patch cannot recover messages typed before RC41 was installed or before the user opted into local learning. Local n-gram recurrence and deterministic grammar heuristics are not equivalent to Google's proprietary models. Neural-level fluency would require a properly licensed, tested local language model and additional performance/privacy work.
