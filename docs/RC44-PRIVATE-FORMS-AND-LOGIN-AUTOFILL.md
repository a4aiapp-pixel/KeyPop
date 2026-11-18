# KeyPop RC44 — Preserved forms, accounts and login/password Autofill

## What was missing from RC43

RC43 retained the original encrypted `KeyPopLoginVault` and a partially wired `KeyPopAutofillService`, but `onSaveRequest()` discarded all submitted credentials and Android Autofill `SaveInfo` was never offered. The app also had no independent form-profile data store, and no portable export/import for logins and profiles. Simply restoring the old source files was not enough to deliver these features.

## RC44 functionality

- **Password vault**: User can manually add accounts; login and password stored only in `keypop-logins.enc` using AES-256-GCM and a nonexportable Android Keystore key. Existing matching username and hostname are updated instead of duplicated.
- **Android system Autofill**: Button on KeyPop Home, and inside Settings → Clipboard & Security, opens Android's **Select Autofill service** prompt. The user must approve KeyPop as the system Autofill provider. This is separate from choosing the keyboard input method.
- **Save usernames and passwords**: When a host app correctly provides a username/password Autofill form, KeyPop offers the Android SaveInfo confirmation UI, then saves only upon `AutofillService.onSaveRequest()` (after user approval). No tracking of keystrokes, no clipboard/prediction harvesting, no silent capture of login secrets.
- **Safe matching**: Browser accounts require one exact website domain. Native app accounts require exact Android package AND the installed app's actual signing-certificate fingerprint; old unbound entries need explicit re-verification, and unverifiable apps do not receive saved credentials.
- **Protected filling**: Device screen lock confirmation before every selected credential or form dataset is delivered to Android Autofill. Candidate displays contain labels only (no passwords). No automatic filling into ambiguous origins.
- **Saved form profiles**: Independently editable local profiles for full name, first/last name, email, phone, organization, street, apartment/unit, city, province/state, postal code and country. Stored only in `keypop-form-profiles.enc` using a separate Android Keystore AES-256-GCM key. Credit cards, CVV, PIN, OTP, passwords and unknown field types are deliberately not stored in this form profile.
- **Optional save forms after confirmation**: If the editor exposes at least two recognized nonsecret fields, the Android save prompt can offer to save them. Settings allow disabling this or disabling password saves independently.
- **Portable encrypted backup**: KeyPop Login Vault → **Export encrypted logins & forms backup** / **Import encrypted logins & forms backup**. Requires a user-provided backup password of at least 12 characters. Entire file authenticated with AES-256-GCM, PBKDF2-HMAC-SHA256 (260k iterations). Imported format and every record verified before merging; existing records are not overwritten or deleted. No plaintext files, no remote service, no cloud requirement.
- **Privacy UI**: The vault and profiles use `FLAG_SECURE` to prevent Android screenshots and task previews, and require a configured device screen lock.

## Upgrade identity and preservation

The permanent signed release continues **`com.aetherkeys.keyboard.stablev2`**, signed with the same production keystore as RC42 and RC43. RC44 raises the release version-code floor to **20,000 + GitHub Actions run number**. A normal `adb install -r`/Android package update keeps the existing `stablev2` private data and Android Keystore keys intact; **do not uninstall or clear storage** before upgrading. The RC43 → RC44 emulator gate checks this same-package in-place installation using the actual previously published RC43 APK.

A separate RC40 debug-preview (`com.aetherkeys.keyboard.previewv140`) or RC43 debug-preview (`.rc43preview`) has an unrelated package ID and cannot have its private data read by the new signed application. If those versions do not have a compatible **encrypted vault export** option, RC44 cannot recover their protected credentials automatically. The RC44 backup format is portable from RC44 onward. Existing passwords in Google Password Manager or another autofill provider are managed by that provider, not silently imported into KeyPop.

## User setup on Android

1. Install the signed RC44 ARM64 APK over signed RC43 without uninstalling it.
2. Open KeyPop → **Saved logins, passwords & form data**; authenticate using the Android screen lock.
3. Add your accounts and form profiles, or import a portable `.kpv` encrypted backup if one exists.
4. Open **Enable automatic form & login filling** and approve KeyPop in Android Autofill settings. This may replace another active Autofill provider; it does not change the keyboard selection.
5. When Android offers to save an entered login or form, approve it only for trustworthy apps/sites. To autofill, select the KeyPop suggestion and unlock the device.
6. Before any uninstall, device reset or switching package IDs, export a portable encrypted backup, preserve the backup password separately, and test restoration. Never send a private vault file or password to the GitHub repository or ChatGPT.

## Tests and limitations

JUnit checks strict field allowlist and prohibition of password/OTP/payment fields. Emulator instrumentation checks encrypted storage, deduplicated accounts, form profile survival across reopened stores, encrypted backup integrity and wrong-password handling, and Autofill-service manifest permission. CI also runs the original RC40 regressions, RC42 URL safety tests, signature matching and RC43 → RC44 installation. Actual browser/web app Autofill availability depends on Android and each application's hints/compatibility; a user-device check is still needed.
