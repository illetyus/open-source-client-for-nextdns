import hashlib
import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import run_browserstack_espresso as runner


class BrowserStackSubmissionTest(unittest.TestCase):
    def test_real_device_payload_excludes_tests_requiring_emulator_preconditions(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            app = root / 'app.apk'
            suite = root / 'tests.apk'
            devices = root / 'devices.json'
            evidence = root / 'package-evidence.json'
            app.write_bytes(b'app')
            suite.write_bytes(b'suite')
            devices.write_text(json.dumps(['Google Pixel 11 Pro XL-17.0']), encoding='utf8')
            evidence.write_text(json.dumps({
                'commit': 'a' * 40,
                'packages': [
                    {'kind': kind, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest(),
                     'signatureVerified': True}
                    for kind, file in [('debug', app), ('androidTest', suite)]
                ],
            }), encoding='utf8')
            args = ['runner', '--app', str(app), '--test-suite', str(suite),
                    '--devices-file', str(devices), '--package-evidence', str(evidence)]
            with patch.object(sys, 'argv', args), patch.dict(runner.os.environ, {
                'BROWSERSTACK_USERNAME': 'test-user',
                'BROWSERSTACK_ACCESS_KEY': 'test-key',
            }), patch.object(runner, '__file__', str(root / 'scripts' / 'runner.py')), \
                    patch.object(runner.urllib.request, 'build_opener') as build_opener:
                opener = build_opener.return_value
                responses = [{'app_url': 'bs://app'}, {'test_suite_url': 'bs://suite'},
                             {'build_id': 'build-id'}]
                opener.open.return_value.__enter__.return_value.read.side_effect = [
                    json.dumps(item).encode() for item in responses
                ]
                runner.main()

            payload = json.loads(opener.open.call_args.args[0].data)
            expected_classes = [
                'com.example.NativeApiFixtureTest',
                'com.example.NativeLegalViewportTest',
                'com.example.NativeLoginNavigationTest',
                'com.example.NativeNotificationPermissionTest',
                'com.example.NativeScreenJourneyTest',
                'com.example.WorldMapStrictModeTest',
            ]
            self.assertEqual(expected_classes, payload['class'])
            self.assertTrue(payload['clearPackageData'])
            submission = json.loads((root / 'build/browserstack/submission.json').read_text())
            self.assertEqual(expected_classes, submission['testClasses'])
