# KeyPop RC43 restoration – source candidate

- Foundation: complete RC40 branch, all Android services, assets, theme/layout/sizing customizations, ML Kit and ONNX integrations, and historical tests preserved.
- Additive RC42 language model, encrypted opt-in phrase memory, explicit save tool, per-app exclusions, URL editing restrictions, grammar suggestions.
- Permanent update identity remains `com.aetherkeys.keyboard.stablev2`. Production signing certificate must match the pinned RC42 SHA-256 in the trusted build repository. No private keystore is included in source.
- Version codes begin at 10000 plus workflow run number (monotonic within this release channel). Future releases must increase that code, keep package ID, and reuse the same certificate.
- RC40 debug `com.aetherkeys.keyboard.previewv140` has a separate package/signature; it cannot be replaced in place by stablev2. Export and restore personal data where supported before removing the old app.
- CI: do not publish until unit, lint, Android 35 emulator and pinned signing certificate verification pass. Real-device WhatsApp, Chrome, Google Translate, and all theme workflows still require manual acceptance.
- This archive is a **source candidate**, not evidence of a finished or tested APK. GitHub Actions is the Android SDK build gate.
