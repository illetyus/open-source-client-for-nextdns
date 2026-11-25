"""Require real release instrumentation completion and a signature-only change."""
import hashlib
import json
import os
import re
from pathlib import Path
from package_payload import digest, entries
from release_runtime_evidence import REQUIRED_RUNTIME_TESTS

root = Path(__file__).resolve().parents[1]
out = root/'build/compliance/runtime'
text = (out/'instrumentation.txt').read_text(encoding='utf8')
native_test_classes = [
    'com.example.NativeApiFixtureTest',
    'com.example.NativeLegalViewportTest',
    'com.example.NativeLoginNavigationTest',
    'com.example.NativeNotificationRuntimeAuditTest',
    'com.example.NativeScreenJourneyTest',
    'com.example.ReleaseSecurityRuntimeTest',
    'com.example.WorldMapStrictModeTest',
]
summaries = [int(count) for count in re.findall(r'OK \((\d+) tests?\)', text)]
assert sum(summaries) == REQUIRED_RUNTIME_TESTS and 'INSTRUMENTATION_CODE: -1' in text, text[-3000:]
assert all(test_class in text for test_class in native_test_classes), 'Every native release test class must appear in instrumentation output'
assert not any(marker in text for marker in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed', 'INSTRUMENTATION_STATUS_CODE: -2'))
statuses = re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$', text, re.M)
assert statuses.count('1') == REQUIRED_RUNTIME_TESTS and statuses.count('0') == REQUIRED_RUNTIME_TESTS, f'Require {REQUIRED_RUNTIME_TESTS} started and successfully completed tests'
assert all(status in ('0', '1') for status in statuses), 'Errors, failures, ignored tests or assumptions are not release evidence'
assert 'RELEASE_AUDIT_SYNTHETIC_CANARY_20261009' not in (out/'logcat.txt').read_text(encoding='utf8'), 'Synthetic credential appeared in logcat'
unsigned = root/'app/build/outputs/apk/release/app-release-unsigned.apk'
signed = out/'release-runtime.apk'
assert entries(unsigned) == entries(signed), 'Runtime APK differs beyond signing records'
beta_sha256 = (out/'beta-original-sha256.txt').read_text(encoding='utf8').strip()
assert re.fullmatch(r'[a-f0-9]{64}', beta_sha256)
assert (out/'beta-upgrade-install.txt').read_text(encoding='utf8').strip() == 'Success'
for filename, method in (
    ('beta-seed.txt', 'seedSettingsInPublishedBeta'),
    ('beta-readback.txt', 'publishedBetaReadsBackSeededSettings'),
    ('beta-upgrade.txt', 'installedUpgradePreservesBetaSettings'),
):
    upgrade_log = (out/filename).read_text(encoding='utf8')
    assert 'INSTRUMENTATION_STATUS: class=com.example.NativeBetaUpgradeTest' in upgrade_log
    assert f'INSTRUMENTATION_STATUS: test={method}' in upgrade_log
    assert 'OK (1 test)' in upgrade_log and 'INSTRUMENTATION_CODE: -1' in upgrade_log
    assert re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$', upgrade_log, re.M) == ['1', '0']
report = {'commit':os.environ['GITHUB_SHA'], 'tests':REQUIRED_RUNTIME_TESTS, 'failures':0, 'skipped':0,
          'nativeTestClasses':native_test_classes,
          'emulator':{'api':35,'image':'system-images;android-35;default;x86_64'},
          'unsignedApkSha256':hashlib.sha256(unsigned.read_bytes()).hexdigest(),
          'runtimeApkSha256':hashlib.sha256(signed.read_bytes()).hexdigest(),
          'payloadSha256':digest(unsigned), 'payloadIdenticalExceptSignature':True,
          'canaryAbsentFromLogcat':True, 'signer':'ephemeral CI key; never a production signing approval',
          'betaUpgrade':{
              'fromTag':'v1.0.0-beta.1', 'publishedApkSha256':beta_sha256,
              'signingScope':'both builds re-signed with the same disposable CI key',
              'preserved':['language', 'theme', 'guest mode', 'stored terms acceptance'],
          },
          'networkScope':'local TLS fixtures, trust rejection, redirect/downgrade and cleartext rejection; no real account or BrowserStack run'}
(out/'release-runtime-evidence.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(f'PASS: {REQUIRED_RUNTIME_TESTS} executed release runtime tests; no canary in logcat; signed/unsigned payload entries identical')

