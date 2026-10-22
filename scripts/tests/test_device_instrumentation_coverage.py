from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class DeviceInstrumentationCoverageTest(unittest.TestCase):
    def test_release_emulator_audit_executes_native_device_flows(self):
        runner = (ROOT / 'scripts/run_release_runtime_audit.sh').read_text(encoding='utf8')
        verifier = (ROOT / 'scripts/verify_release_runtime.py').read_text(encoding='utf8')

        required_classes = {
            'NativeApiFixtureTest',
            'NativeLegalViewportTest',
            'NativeLoginNavigationTest',
            'NativeNotificationPermissionTest',
            'ReleaseSecurityRuntimeTest',
            'WorldMapStrictModeTest',
        }
        invoked_classes = set(re.findall(r'com\.example\.([A-Za-z0-9_]+)', runner))
        self.assertLessEqual(required_classes, invoked_classes)
        self.assertIn('OK (17 tests)', verifier)
        self.assertIn("statuses.count('1') == 17 and statuses.count('0') == 17", verifier)
        self.assertIn("'tests':17", verifier)


if __name__ == '__main__':
    unittest.main()
