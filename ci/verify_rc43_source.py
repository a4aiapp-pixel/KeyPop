from pathlib import Path
p=Path(__file__).resolve().parents[1]
source=(p/'app/src/main/java/com/aetherkeys/keyboard/AetherImeService.kt').read_text()
base=(p/'app/build.gradle.kts').read_text()
for token in ('KeyPopAdaptiveServiceController', 'KeyPopUrlEditingPolicy',
              'KeyPopUnifiedPrediction.rank(', 'KeyPopPersonalLanguageModel(',
              'KeyPopEnterPolicy.insertsNewline(info)', 'deleteBackwardFromEditor',
              'refreshRecentScreenshotAsync', 'KeyPopVisualProbe.capture',
              'KeyPopAdaptiveCandidatePlanner', 'Remember phrase'):
    assert token in source, f'Missing integration: {token}'
for token in ('applicationIdSuffix = ".stablev2"', 'versionCode = 10000',
              'com.google.mlkit:translate', 'com.microsoft.onnxruntime:onnxruntime-android'):
    assert token in base, f'Missing upgrade/performance requirement: {token}'
for name in ['KeyPopLiveKeyEditor.kt','KeyPopModelDownloadService.kt',
             'KeyPopSuggestionStyle.kt','KeyPopOfflineSindhiEngine.kt',
             'KeyPopAdaptiveHistoryStore.kt','KeyPopAdaptiveLanguageModel.kt',
             'KeyPopUrlEditingPolicy.kt']:
    assert (p/'app/src/main/java/com/aetherkeys/keyboard'/name).is_file(), name
print('PASS: RC40 core and RC42 additive source feature gates')
