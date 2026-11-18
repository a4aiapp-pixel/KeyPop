# KeyPop RC42+ — single-app upgrades and stable signing

## Platform constraint
Android **in-place** APK updates require two invariants:
1. **Exactly the same applicationId**, including any build-type suffix.
2. **Exactly the same signing certificate**, with a newer versionCode.

Earlier RC39 and RC40 GitHub debug previews were signed with ephemeral keys and
have unrelated package names. RC42 cannot replace those installs. Remove the
old preview only after exporting any desired data and selecting RC42 as the
current keyboard.

## Permanent RC42+ production channel
- App package: `com.aetherkeys.keyboard.stablev2` (do not rename).
- Baseline: `stableBaseVersionCode = 100`, `versionName = 1.0.42-rc42`.
- GitHub Actions automatically adds the workflow's increasing
  `GITHUB_RUN_NUMBER` to the baseline, so later CI APKs have newer version
  codes without manual numbering. **Keep this workflow's file path and
  history**; resetting its run-number sequence could break upgrades.
- If building outside this CI, set a versionCode higher than the latest
  installed signed version, and reuse the identical package and certificate.
  Never use a fresh default debug keystore for the stable channel.
- Use a stable **private** keystore stored only in GitHub Actions encrypted
  repository/environment secrets or a secure local password manager.
- Do **not** commit the keystore, private key, or signing passwords to Git.

Four GitHub Actions secrets are required for signed variants:
`KEYPOP_SIGNING_KEYSTORE_B64` (base64-encoded JKS/P12 bytes),
`KEYPOP_SIGNING_STORE_PASSWORD`, `KEYPOP_SIGNING_KEY_ALIAS`,
and `KEYPOP_SIGNING_KEY_PASSWORD`.

Generate the keystore privately **once** on a trusted computer; save it safely
because losing this key prevents upgrading this package:

```bash
keytool -genkeypair -v -keystore keypop-stable-v2.jks -storetype JKS \
  -alias keypopstable -keyalg RSA -keysize 3072 -validity 10000
base64 -w0 keypop-stable-v2.jks
```

Copy the base64 output to the `KEYPOP_SIGNING_KEYSTORE_B64` secret using
GitHub repository Settings → Secrets and variables → Actions, not into
an issue, file, or chat. Record the keystore and password in an offline
backup. The CI workflow only publishes **signed** RC42 release candidates
after the tests and Android emulator gates pass.

## Signature upgrade test
Install the first stable-signed APK on a test Android phone/emulator. Build
the next version with **exactly the same key and package**, increment its
versionCode and run `adb install -r new.apk`. Verify the APK installs
successfully, retained preferences remain, and there is still only one
stablev2 package.

The parallel `com.aetherkeys.keyboard.rc42preview` engineering package is
not a production upgrade channel unless it, too, is consistently signed.
Debug artifacts produced without the four secrets can be installed for
testing, but Android will refuse replacement by a new unrelated debug signer.

## Never claim success prematurely
A successful unit-test build does not demonstrate:
- WhatsApp Enter behaviour in the real WhatsApp app;
- no Chrome URL editing/truncation in production;
- historical personal-memory recognition without user consent;
- an upgrade across unrelated package IDs/signing certificates.
