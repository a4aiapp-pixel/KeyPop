# RC42 URL/email truncation and editing integrity — mandatory integration gate

The user reports URL text being truncated and becoming difficult or impossible to edit. The root cause **cannot be confirmed** without a full checkout of the exact installed APK's `AetherImeService.kt` and a logcat/editor repro. Chrome can visually shorten a URL in its omnibox; *visual elision* is distinct from changing the text actually stored in an editor. Do not confuse the two.

## Implemented in this overlay

- `KeyPopUrlEditingPolicy.kt` classifies explicit URI/email editors and URL-like tokens in generic Chrome search/address input. It checks text on **both** sides of the caret so an edit in the middle of a URL remains protected. It also conservatively treats visible path/query fragments as URL-like when the scheme has fallen outside the bounded snapshot. A host IME should additionally retain a per-editor URL-token mode until a word boundary, since arbitrary-length URLs can exceed any fixed context window.
- URI/email editors must not use grammar or personal predictions or automatic punctuation/spacing. Normal search phrases must continue receiving suggestions. The `KeyPopAdaptiveEditorPolicy` now suppresses **all adaptive suggestions** for explicit URI/email fields, and the `KeyPopAdaptiveServiceController.predict` disables predictions for URLs embedded in generic input fields.
- `KeyPopAdaptiveCandidatePlanner.makePlan()` rejects changing a URL-like token. This is a defense-in-depth guard, not a replacement for correct `InputConnection` integration.
- Local pure tests and Android baseline `KeyPopRC42UrlEditingInstrumentedTest.kt` cover long URLs, selection, deleting, cursor-middle editing, URL fragments and preserved suffixes.

## Required changes in the REAL `AetherImeService.kt` (not in this source overlay)

1. At the very beginning of each `press(value)`, determine the editor's input variation from `currentInputEditorInfo?.inputType` and classify the token touching the caret using `KeyPopUrlEditingPolicy.decide(...)` with bounded **actual** `getTextBeforeCursor` and `getTextAfterCursor` snapshots, not displayed suggestion labels. For URI/email input variations, the policy must apply even before any URL characters have been typed.
2. For protected URL contexts, handle normal character keys by committing the **literal key value** through the current input connection. Avoid auto-space, double-space punctuation, grammar rewrite, auto-completion, transliteration, address-memory lookup and pre-emptive text replacement. Backspace, selection, cursor navigation, Enter's host action and copy/paste must retain standard editor semantics.
3. Do not call `setText`, `finishComposingText` with an inconsistent composing region, or delete/reinsert the full URL merely to update predictions. A prediction context snapshot may be bounded, but **never truncate the actual host editor value** to that length.
4. When the caret is inside a URL, suppress candidate application and any stale pending auto-space. Invalidate queued candidate work after cursor movement, selection, package switch and editor restart.
5. Preserve full URL for editing independently of Chrome's visible one-line ellipsis. If the editor only shows a short address, inspect `getExtractedText()` and on-screen text **without** copying the contents to logs. Only then determine whether truncation occurred in storage.
6. If the host supplies an incomplete or null input connection, do not assume deletion/replacement succeeded. Fall back to appropriate `KEYCODE_DEL` or the editor action only where safe. Do not force keyboard dismissal or cursor repositioning on normal URL edits.

## Test matrix (release blocker)

- Chrome address bar typing `https://example.com/a/long/path?q=value&x=1`; edit in the middle, delete selected substring and paste a replacement; confirm entire URL is preserved.
- Very long URL (400+ characters) including `%2F`, `?`, `#`, `&` and Unicode domain/path characters.
- Chrome search phrase `You a` with no URL token: grammar suggestions still visible.
- WhatsApp pasted link with caret inside the URL: no unexpected spaces or punctuation; normal surrounding chat unaffected.
- Gmail address field and editable URL fields in other apps, including browser-incognito and cursor-movement scenarios.
- All relevant keyboards in the same run: Backspace select-all and Unicode, WhatsApp multiline Enter/focus, suggestion auto-space, and RC42 adaptive phrase candidates.

Neither the local tests nor a generic Android `EditText` emulation proves WhatsApp/Chrome behavior on the user's phone. A fully integrated and signed APK must pass an actual emulator run and device acceptance tests before claiming the URL bug fixed.
