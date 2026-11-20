# KeyPop vs Gboard — engineering parity and quality acceptance

Updated: 2026-11-20. **Status: parity and superiority NOT established.**
This document is an evidence register, not a claim that an open-source keyboard duplicates Google's proprietary models.

## User-reported acceptance requirements
1. Universal gesture/glide typing, enabled or disabled globally, must work with every theme, English layout (QWERTY, AZERTY, QWERTZ, Dvorak, Colemak), user-customized letter layout, Urdu and Sindhi. Typing must still work by taps when off. Settings must be immediately effective.
2. A gesture visibly traces the keys, tolerates a small number of imperfect samples, and avoids writing to secure, numeric, email, URL or other restricted editor fields.
3. Frequent typed words, accepted spelling choices, n-gram continuations and words at the beginning of messages must persist securely between sessions; incognito and sensitive fields must never be learned.
4. User-supplied examples (Do3s -> Does, te -> the in appropriate context, thmes -> themes, miatyped -> mistyped, googke -> Google, deliverab -> deliverable) plus unseen errors. Preserve valid personal names and mixed English/Urdu/Sindhi text.
5. Auto-capitalization must work on first focus and after sentence punctuation, with independent visible switches.
6. Intelligent grammar: subject–verb agreement, verb auxiliaries, repeated words and article hints. No ungrammatical automatic edits in questions, idioms or names.
7. No regressions in theme differentiation, live switching, resize, Chrome/search, WhatsApp Enter, secure autofill, selection, data preservation and stable signed in-place upgrades.

## Current implementation (RC51 source; verify after CI)
- Global `glide`, optional `glideTrailVisible`, sensitivity `glideSensitivityDp` under **Settings -> Typing**.
- Theme-independent `KeyPopGlidePolicy`, interpolated touch samples, line overlay and English/Urdu/Sindhi `KeyPopGlideScoring` with a bounded local lexicon.
- RC50 short-word regression coverage, RC49 local encrypted word frequencies and phrase-history context, RC48 capitalization and design presets.
- Expanded conservative offline grammar rules. These are NOT an AI proofreading model and do not deliver Gboard's entire writing-tools functionality.

## Test matrix
| Requirement | Automated test | Manual paired test | Acceptance threshold |
| --- | --- | --- | --- |
| Toggle available and persistent | RC51GlideInstrumentedTest | 15 visual themes x 3 languages | 100% reachable and persistent |
| Language & layout support | RC51GlideGrammarTest + RC51GlideInstrumentedTest | 5 English layouts + >=10 custom swaps; Urdu; Sindhi | 100% no missing alphabetic keys |
| Glide decoding | unit/native instrumented tests | 300 word gestures per keyboard, repeated at 3 speeds | key-to-word top-1 >= Gboard on same dataset |
| Noise tolerance | repeated/extra/missed sample regression | >=100 noisy trace gestures | top-1 >= Gboard; no unintended inserts |
| Short-word correction | RC50ShortWordRegressionTest | >=200 genuine/typo minimal pairs | net correction accuracy >= Gboard |
| Multiword prediction | RC49PersonalLearningInstrumentedTest | train/holdout 500 phrase continuations per device | top-3 recall >= Gboard |
| Grammar correctness | RC51GlideGrammarTest | >=300 human-reviewed sentences incl. auxiliaries | precision and recall >= Gboard |
| Privacy and incognito | policy and instrumented test | 75 sensitive fields/app combinations | zero learned or inadvertently inserted private text |
| Stability and latency | Gradle unit/lint + emulator smoke | 10,000 taps and 1,000 gestures on low/mid/high-end devices | no crashes; key input latency p95 no worse than Gboard |
| Upgrade safety | Android emulator signed in-place RC50->RC51 | install over preserved personal setup | same signature/package; settings, vault and learned words intact |

## Measurement rules
- Use the **same Android device**, keyboard languages, dictionaries, training phrases, typed targets, input apps and text fields; randomized order, multiple trials and cold/warm starts.
- Record each keyboard's app version/build, Android build, corpus revision, confusion matrix, top-1/top-3 recall, precision, incorrect replacement rate, median/p95 latency and crash rate.
- Exclude Gboard features not supported on the test device from directly comparable claims. Label non-comparable functions individually.
- Reserve 20% unseen holdout examples, including names, mixed-script words, genuine punctuation and ambiguous colloquialisms. Never tune on the holdout.
- Report 95% bootstrap confidence intervals. Claim KeyPop **beats** Gboard only where the predeclared metric is higher with convincing uncertainty and no material privacy regressions.
- Mark any unevaluated row **NOT VERIFIED**. Do not claim “100% parity” or “better than Gboard” from passing local tests alone.

## Evidence so far
- RC50: signed ARM64/universal GitHub release, unit/lint and emulator RC49->RC50 passed.
- RC51: source changes and regression suites committed; release CI evidence must be linked after successful execution.
- Gboard: official Google documentation lists glide typing, suggestions and autocorrect, personalization, gesture cursor/delete controls, and (on eligible devices) advanced proofread/writing tools; model internals are proprietary. Local implementation cannot imply identical model quality.
- **Paired Gboard measurements: NONE YET. Not certified.**
