"""Require real release instrumentation completion and a signature-only change."""
import hashlib
import json
import os
import re
from pathlib import Path
from package_payload import digest, entries

root = Path(__file__).resolve().parents[1]
out = root/'build/compliance/runtime'
text = (out/'instrumentation.txt').read_text(encoding='utf8')
assert 'OK (12 tests)' in text and 'INSTRUMENTATION_CODE: -1' in text, text[-3000:]
assert 'com.example.WorldMapStrictModeTest' in text, 'StrictMode checks must run in the release runtime audit'
assert not any(marker in text for marker in ('FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed', 'INSTRUMENTATION_STATUS_CODE: -2'))
statuses = re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$', text, re.M)
assert statuses.count('1') == 12 and statuses.count('0') == 12, 'Require twelve started and successfully completed tests'
assert all(status in ('0', '1') for status in statuses), 'Errors, failures, ignored tests or assumptions are not release evidence'
assert 'RELEASE_AUDIT_SYNTHETIC_CANARY_20261009' not in (out/'logcat.txt').read_text(encoding='utf8'), 'Synthetic credential appeared in logcat'
unsigned = root/'app/build/outputs/apk/release/app-release-unsigned.apk'
signed = out/'release-runtime.apk'
assert entries(unsigned) == entries(signed), 'Runtime APK differs beyond signing records'
report = {'commit':os.environ['GITHUB_SHA'], 'tests':12, 'failures':0, 'skipped':0,
          'emulator':{'api':35,'image':'system-images;android-35;default;x86_64'},
          'unsignedApkSha256':hashlib.sha256(unsigned.read_bytes()).hexdigest(),
          'runtimeApkSha256':hashlib.sha256(signed.read_bytes()).hexdigest(),
          'payloadSha256':digest(unsigned), 'payloadIdenticalExceptSignature':True,
          'canaryAbsentFromLogcat':True, 'signer':'ephemeral CI key; never a production signing approval',
          'networkScope':'local TLS fixtures, trust rejection, redirect/downgrade and cleartext rejection; no real account or BrowserStack run'}
(out/'release-runtime-evidence.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('PASS: 12 executed release runtime tests; no canary in logcat; signed/unsigned payload entries identical')

