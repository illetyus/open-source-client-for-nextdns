"""Record signed debug app/test artifacts for a real-device screen-test run."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
build_tools = sorted((sdk / 'build-tools').iterdir(), key=lambda p: tuple(int(n) for n in re.findall(r'\d+', p.name)))[-1]
apksigner = build_tools / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
packages = []
signers = []
for kind, relative in (
    ('debug', 'app/build/outputs/apk/debug/app-debug.apk'),
    ('androidTest', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'),
):
    path = root / relative
    result = subprocess.run([str(apksigner), 'verify', '--verbose', '--print-certs', str(path)],
                            check=True, capture_output=True, text=True)
    signer = re.search(r'certificate SHA-256 digest: ([a-f0-9]{64})', result.stdout).group(1)
    signers.append(signer)
    packages.append({'kind': kind, 'file': relative, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                     'bytes': path.stat().st_size, 'signatureVerified': True, 'signerSha256': signer})
if len(set(signers)) != 1:
    raise SystemExit('App and instrumentation APK signers differ.')
evidence = {'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
            'packages': packages, 'purpose': 'Espresso screen journeys; execution pending',
            'productionSigningApproved': False}
destination = root / 'build/screen-tests/package-evidence.json'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
print('Screen-test APK hashes and matching signatures recorded; device results remain pending.')
