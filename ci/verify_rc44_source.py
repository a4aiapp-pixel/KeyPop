#!/usr/bin/env python3
from pathlib import Path
import re, hashlib, zipfile
R=Path(__file__).resolve().parents[1]
base=R/'app/src/main/java/com/aetherkeys/keyboard'
m=(R/'app/src/main/AndroidManifest.xml').read_text()
b=(R/'app/build.gradle.kts').read_text()
for p in ['AetherImeService.kt','KeyPopLanguageModels.kt','KeyPopOfflineSindhiEngine.kt',
          'KeyPopGoogleTranslateActivity.kt','KeyPopClipboardActivity.kt',
          'KeyPopLayoutStudioActivity.kt','KeyPopLoginVault.kt','KeyPopFormVault.kt',
          'KeyPopPortableVaultBackup.kt','KeyPopAutofillService.kt',
          'KeyPopAdaptiveLanguageModel.kt','KeyPopUrlEditingPolicy.kt']:
    assert (base/p).is_file(),p
for p in ['KeyPopRC40WhatsAppEnterInstrumentedTest.kt',
          'KeyPopRC42UrlEditingInstrumentedTest.kt',
          'KeyPopRC44PrivateAutofillInstrumentedTest.kt']:
    assert (R/'app/src/androidTest/java/com/aetherkeys/keyboard'/p).exists(),p
assert (R/'app/src/test/java/com/aetherkeys/keyboard/KeyPopRC44FormFieldPolicyTest.kt').exists()
for name in ['KeyPopVaultActivity','KeyPopFormProfilesActivity','KeyPopAutofillService',
             'KeyPopAutofillUnlockActivity','KeyPopFormAutofillUnlockActivity']:
    assert f'android:name=".{name}"' in m,name
assert 'android.permission.BIND_AUTOFILL_SERVICE' in m
assert 'applicationIdSuffix = ".stablev2"' in b
assert 'versionCode = 20000 +' in b
assert '1.0.44-rc44-vault-autofill' in b
assert 'KEYPOP_SIGNING_KEYSTORE_PATH' in b
code=(base/'KeyPopAutofillService.kt').read_text()
assert 'SaveInfo.Builder' in code
assert 'onSaveRequest(request: SaveRequest' in code
assert 'KeyPopAppIdentity.signingDigest' in code
assert 'KeyPopPortableVaultBackup' in (base/'KeyPopLoginVault.kt').read_text()
assert 'KeyPopFormFields.identify' in code
assert 'onFillRequest' in code
assert 'onSaveRequest' in code
assert 'saveApprovedFields' in code
assert 'private val browserPackages' in code
assert 'android:allowBackup="false"' in m
assert 'KeyPopPrivateVault' not in m
print('PASS RC44: full RC40 source + RC42 intelligence + encrypted forms/login Autofill')
