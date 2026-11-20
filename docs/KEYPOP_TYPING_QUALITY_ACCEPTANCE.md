# KeyPop typing-quality acceptance and Gboard parity protocol

**Version:** 1.0 — 20 November 2026  
**Applicable build:** RC52 and subsequent signed upgrades  
**Status:** NOT VERIFIED for Gboard parity or superiority. No paired real-device results have yet been supplied.

## What the benchmarks actually test

1. **Local Android engine test:** `KeyPopTypingQualityBenchmarkTest` measures top-1 and top-3 ranking against 40 unfamiliar spelling errors, 12 user-reported regression examples, 24 multiword prompts, and positive/negative grammar cases using the installed KeyPop lexicon. It prints `KP_QUALITY` records to the Android test output, including the failures and measured engine computation time. Holdout examples are kept in **androidTest** and are not included in the production lexicon/phrase model.
2. **Real paired-device test:** Two keyboards must be tested in the **same Android apps on the same device**, with identical languages, dictionaries, layouts and text. Randomize keyboard order and repeat difficult examples. Record only anonymized case IDs and correctness outcomes in `quality/paired_results_template.csv` (no private message text, passwords, email addresses or saved form values).
3. **Statistical comparator:** `python3 scripts/keypop_quality_compare.py quality/real_paired_results.csv` reports separate spelling, next-word, grammar and personalized-vocabulary top-1/top-3 scores; unwanted edits on correct text; and protected-field errors. It calculates deterministic **95% paired bootstrap intervals**, identifies low sample size and never fabricates Gboard observations.

## Data separation to prevent misleading claims

- `SPELL_REGRESSION` consists of already reported cases such as `im → I'm`, `Do3s → does`, and `te → the`. **It is not an unseen accuracy test.**
- `SPELL_HOLDOUT` has 40 varied one- and two-edit errors that do not live in a dedicated test-specific correction table. Because the test is public, future engineering changes must not add special-case corrections solely to improve these scores. Maintain a separate, blinded evaluation set outside the production repository for any public claim of superiority.
- `NEXT_HOLDOUT` accepts multiple linguistically plausible continuations; "keep me posted" must offer **on, about, regarding** rather than a single hardcoded answer. Include previously unseen nouns, tense changes, sentence boundaries and dialects.
- `GRAMMAR_RECALL` measures eligible corrections. `GRAMMAR_FALSE_EDITS` measures unwanted suggestions on *already correct* sentences, names and acronyms. A system that changes correct text frequently fails regardless of recall.
- Locally cached, encrypted personal learning is measured only on explicitly permitted non-sensitive text. Training instances must **not** be counted as evaluation instances; use a training/held-out phrase split.

## Mandatory actual Android app matrix

| Environment | Required checks |
| --- | --- |
| Chrome omnibox, typed search | Live suggestions, correction of natural-language queries, never modify actual URLs |
| Chrome web forms/search widgets | Suggestion strip appearing consistently, focus retention, selection and cursor behavior |
| WhatsApp composer | Suggestions and contractions, Enter/newline vs Send, emoji, cursor and on-screen keyboard |
| Gmail or email client message body | Formal grammar and proofreading, sentence capitalization, normal text fields |
| Notes/Docs or third-party editor | Paragraph editing, preceding-word correction, long contexts, typed-word undo |
| Password, email, OTP and URL fields | Zero unintended corrections, zero learning and no leaks |
| Native-script and custom layout | Urdu/Sindhi script, mixed Latin phrases, glide gesture, accessibility and all major themes |

**Real-device evidence** requires screenshots or structured observations with no private data. The emulator's KeyPop test activity is **not** evidence that Chrome or WhatsApp was tested. Missing apps must be marked **NOT RUN**, not passed. Test portrait and landscape, and use both newly installed and upgraded KeyPop.

## Next-word accuracy: measured baseline versus desired target

- **Verified RC52 engine baseline:** next-word top-1 **12/24 = 50.0%**; top-3 **19/24 = 79.2%**, on the original small fixed Android emulator corpus. These figures are *not* 100%, and are not Chrome/WhatsApp or Gboard measurements.
- **100% is the aspirational quality target, not a currently measured result or a defensible guarantee across open-ended language.** Everyday sentences often have several equally natural continuations. The evaluation must predeclare acceptable alternatives and measure both top-1 quality and top-3 recall on a substantially larger, independently curated and blinded corpus.
- **No training on benchmark cases.** Do not add those prompts, their exact expected continuations or other evaluation-only strings to the production phrase bank to manufacture accuracy. Improvements must generalize to unseen contexts and pass false-positive and latency checks.
- **Diagnostic requirement:** report wrong top-1 choices and their top-three alternatives for every failed case. Correct context retrieval, agreement, ranking and personalized weighting as *classes*, not as individual lookups.
- **Release gate:** target 100% correct top-1 and top-3 outcomes on a separately maintained, representative, blinded acceptance corpus (using predefined linguistically valid continuations) and zero unintended modifications to valid or sensitive text. Never claim 100% next-word accuracy without actual independently collected real-app evidence meeting that target; always publish the measured denominator, keyboard configuration, acceptable-word labeling method and confidence interval.

## Scoring definitions

- **Top-1:** intended correction or one acceptable next word is first. **Top-3:** it appears in first three candidates. Respect the three-slot visible strip; internal top-8 recall alone does not count as a UI success.
- **Autocorrection precision:** proportion of automatic edits that are linguistically correct. **False edits:** edits applied to already correct text. Report *both* since boosting recall by editing everything is harmful.
- **Grammar precision/recall:** manually label acceptable changes and unchanged valid sentences, including idioms, contractions, questions and names.
- **Personalization:** measure unseen held-out continuations before and after learning permitted phrases. Compare personalized gain, not just memorized training phrases.
- **Latency:** p50 and p95 from real keypress to painted suggestions on mid-range Android devices. Engine compute p95 is a useful diagnostic, **not** equivalent to touch-to-display latency.
- **Reliability:** out-of-app text, full URLs, focus changes, undo behavior, IME restarts, crashes and persistence. No private text should appear in diagnostics.
- **Paired comparison:** use 400 or more actual matched observations, at least 50 in each core quality category, at least three real Android apps, and dedicated privacy/valid-text cases. The comparator requires 95% intervals with no core-category decrease worse than the predeclared 3-percentage-point margin and no excess false edits. For superiority, the aggregate top-1 lower CI must be strictly positive without other regressions.

## How to run and record

1. In GitHub Actions, run the **KeyPop Typing Quality Benchmark** workflow. Review `KP_QUALITY` lines in the job output / summary. Those results provide a verified KeyPop-only **engine baseline**, not Gboard numbers.
2. Install the same signed RC52 APK on a supported Android phone alongside Gboard. For each anonymized test case, use the same app/text field and switch between keyboards. Enter the desired outcome and select whether each keyboard's first and first-three suggestions were acceptable; record any unwanted automatic edits.
3. Copy the empty `quality/paired_results_template.csv` to `quality/real_paired_results.csv` **locally** and enter only anonymized measurements. Do not commit private text or account data. Evaluate with the command above, preferably on your local computer. For the user-facing compliance report, upload only aggregated results if desired.
4. Fix low-coverage and false-positive *classes* (retrieval, ranking, context, morphology, personalization), rerun the untouched holdout, and compare the same versioned corpus on both keyboards.

**Do not certify "100% parity" or "better than Gboard"** from compiling an APK, a few passing examples, a developer-generated dataset alone, or Gboard marketing claims. Missing external trials explicitly fail certification, not the software build.
