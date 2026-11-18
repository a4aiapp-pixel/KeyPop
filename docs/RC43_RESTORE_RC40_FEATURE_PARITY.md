# KeyPop RC43 — RC40 feature restoration and regression audit

## Current status (2026-11-18)
**BLOCKER: RC40 source is not in this repository.** The currently connected GitHub App has access only to `a4aiapp-pixel/KeyPop`. The previous verified RC40 source was in `amjadrajamemon/KeyPop`, branch `feature/rc40-whatsapp-enter-focus-20261113`, release `v1.0.39-rc40-debug-preview`. That repository currently returns NOT_FOUND to the installed GitHub connector.

The existing new repo contains `KeyPop-main.zip` (an older 0.2.0 source application), `KeyPop-RC42-Intelligence-URL-Safety-GitReady-Overlay.zip` (overlay, not a complete RC40 application), and staged RC42 sources. RC42 therefore does **not** preserve complete RC40 functionality. Android emulator and JUnit success on the slim RC42 base do not prove RC40 feature parity.

**Never mark this restoration complete without obtaining and merging actual RC40 source plus compiling and testing the combined app.**

## Critical invariants
- Keep permanent release identity `com.aetherkeys.keyboard.stablev2`, the same pinned certificate (public SHA-256 in `docs/KEYPOP_STABLE_SIGNING_CERT_SHA256.txt`), and strictly increasing versionCode (GitHub run number).
- Keep signing material ONLY in encrypted GitHub Actions secrets; never commit the private keystore or credentials.
- Preserve RC42 adaptive prediction engine, encrypted/opt-in phrase learning, address memory separate consent, URL/email edit guards, and all existing RC42 regression tests.
- Keep existing user's installed RC40 preview available during transition; RC40 preview's package and signature cannot be replaced directly with a new stablev2 APK.
- Do not silently replace the full RC40 app with `KeyPop-main.zip` again.

## RC40 feature-parity restoration checklist (source-required)
- [ ] Retrieve RC40 branch source from `amjadrajamemon/KeyPop` or a full RC40 source ZIP uploaded by owner.
- [ ] Compare entire `app/src/main`, `app/src/test`, `app/src/androidTest`, `app/build.gradle.kts`, `res`, CI, settings and assets against RC42.
- [ ] Preserve RC40 suggestion strip rendering and lifecycle, including Chrome-specific absence bug, UI/IME focus safeguards, and editor compatibility.
- [ ] Preserve RC39 Backspace selected-text deletion, WhatsApp Enter action, key focus and no-keyboard-dismiss behavior, and RC38 suggestion auto-spacing fixes.
- [ ] Preserve RC40 theme/layout, resizing/styling and customization flows, all resource files, UI polish and user settings/data handling.
- [ ] Preserve RC42 adaptive word/grammar/phrase prediction, explicit local learning consent, per-app exclusions and encrypted memory.
- [ ] Preserve URL character-by-character editing, long-URL suffix, text selection, no unwanted autocorrect/spacing in URI/email and URL-like Chrome omnibox fields.
- [ ] Preserve Android app/package/keystore upgrade invariants (stablev2 only for new permanent channel).
- [ ] Integrate RC40-specific unit and instrumentation tests in addition to RC42 tests; run Android 35 emulator, lint, build, selected-text, WhatsApp/Chrome editor mocks.
- [ ] Verify ARM64 and universal signed release assets are generated, signatures match pinned cert and version code increases.
- [ ] Perform real-device regression on WhatsApp/Chrome; emulator tests cannot fully prove behavior of those external apps.

## Minimal phone-only action to unblock full restoration
From the phone signed in to the original GitHub account, open `https://github.com/amjadrajamemon/KeyPop/tree/feature/rc40-whatsapp-enter-focus-20261113`, switch browser to Desktop site if Code menu isn't visible, choose Code -> Download ZIP, then attach that ZIP to the ChatGPT conversation **or** upload to the new `a4aiapp-pixel/KeyPop` repository. No PC or Termux is required. Do not upload a private signing key. Alternatively connect the old account/repo through ChatGPT GitHub so the source is readable.

Until the exact RC40 source is available, the only defensible status is **RC40 restoration pending**. This branch is a safe recovery starting point; it does not change or overwrite the currently published signed RC42 APK.
