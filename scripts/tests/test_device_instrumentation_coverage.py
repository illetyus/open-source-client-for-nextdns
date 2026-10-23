from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class DeviceInstrumentationCoverageTest(unittest.TestCase):
    def test_release_emulator_audit_executes_native_device_flows(self):
        runner = (ROOT / 'scripts/run_release_runtime_audit.sh').read_text(encoding='utf8')
        verifier = (ROOT / 'scripts/verify_release_runtime.py').read_text(encoding='utf8')
        helper = (ROOT / 'scripts/release_runtime_evidence.py').read_text(encoding='utf8')

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
        for class_name in required_classes:
            self.assertIn(f'com.example.{class_name}', verifier)
        self.assertIn("sum(summaries) == REQUIRED_RUNTIME_TESTS", verifier)
        self.assertIn("statuses.count('1') == REQUIRED_RUNTIME_TESTS and statuses.count('0') == REQUIRED_RUNTIME_TESTS", verifier)
        self.assertIn("'tests':REQUIRED_RUNTIME_TESTS", verifier)
        self.assertIn("REQUIRED_RUNTIME_TESTS = 17", helper)

    def test_permission_denial_is_prepared_outside_the_instrumented_process(self):
        runner = (ROOT / 'scripts/run_release_runtime_audit.sh').read_text(encoding='utf8')
        denied_test = 'com.example.NativeNotificationPermissionTest#android13PermissionDenialPreventsPrivateNotifications'
        granted_test = 'com.example.NativeNotificationPermissionTest#grantedPermissionPostsPrivateNotificationsWithGenericPublicVersions'
        self.assertIn('adb shell pm revoke com.aistudio.nextdns.mgrqvt android.permission.POST_NOTIFICATIONS', runner)
        self.assertIn(denied_test, runner)
        self.assertIn('adb shell pm grant com.aistudio.nextdns.mgrqvt android.permission.POST_NOTIFICATIONS', runner)
        self.assertIn(granted_test, runner)
        self.assertLess(runner.index('adb shell pm revoke'), runner.index(denied_test))
        self.assertLess(runner.index(denied_test), runner.index('adb shell pm grant'))
        self.assertLess(runner.index('adb shell pm grant'), runner.index(granted_test))
        self.assertEqual(2, runner.count('am instrument'))
        self.assertIn('instrumentation-denied.txt', runner)
        self.assertIn('instrumentation-granted.txt', runner)
        notification_test = (ROOT / 'app/src/androidTest/java/com/example/NativeNotificationPermissionTest.kt').read_text(encoding='utf8')
        for method in ("android13PermissionDenialPreventsPrivateNotifications", "grantedPermissionPostsPrivateNotificationsWithGenericPublicVersions"):
            body = notification_test.split(f"fun {method}()", 1)[1].split("@Test", 1)[0]
            self.assertNotIn("revokeRuntimePermission", body)
            self.assertNotIn("grantRuntimePermission", body)

    def test_native_login_reaches_settings_legal_section_and_viewport_checks_revisions(self):
        viewport = (ROOT / 'app/src/androidTest/java/com/example/NativeLegalViewportTest.kt').read_text(encoding='utf8')
        navigation = (ROOT / 'app/src/androidTest/java/com/example/NativeLoginNavigationTest.kt').read_text(encoding='utf8')

        self.assertIn('LegalDocuments.load(context, language).getOrThrow()', viewport)
        self.assertNotIn('privacy-2026-10-DRAFT-2', viewport)
        self.assertIn('performScrollToNode(hasTestTag("legal_privacy_link"))', navigation)
        self.assertIn('rule.onNodeWithTag("legal_privacy_link").assertExists().performClick()', navigation)
        self.assertNotIn('UiDevice', navigation)


if __name__ == '__main__':
    unittest.main()
