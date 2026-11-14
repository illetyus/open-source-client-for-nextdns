import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_runtime_evidence import REQUIRED_RUNTIME_TESTS, instrumentation_failure_summary, validate_runtime_summary


class ReleaseRuntimeEvidenceTest(unittest.TestCase):
    def test_failure_summary_retains_early_and_late_failures(self):
        output = (
            "1) first(com.example.AuditTest)\n"
            "java.lang.NoSuchMethodError: missing binding\n"
            "    at com.example.AuditTest.first(AuditTest.kt:5)\n"
            + "    at android.app.Instrumentation.run(Instrumentation.java:1)\n" * 90
            + "2) last(com.example.AuditTest)\n"
            "java.lang.AssertionError: unexpected result\n"
            "Tests run: 2, Failures: 2\n"
        )
        summary = instrumentation_failure_summary(output)
        self.assertIn("NoSuchMethodError", summary)
        self.assertIn("AuditTest.kt:5", summary)
        self.assertIn("AssertionError", summary)
        self.assertNotIn("android.app.Instrumentation.run", summary)

    def summary(self, **updates):
        value = {"tests": REQUIRED_RUNTIME_TESTS, "failures": 0, "skipped": 0}
        value.update(updates)
        return value

    def test_accepts_current_complete_runtime_evidence(self):
        validate_runtime_summary(self.summary(tests=REQUIRED_RUNTIME_TESTS))

    def test_rejects_old_runtime_test_count(self):
        for old_count in (10, 12, 17):
            with self.subTest(old_count=old_count):
                with self.assertRaisesRegex(ValueError, f"exactly {REQUIRED_RUNTIME_TESTS} tests"):
                    validate_runtime_summary(self.summary(tests=old_count))

    def test_release_runtime_verifier_keeps_all_p06_classes_and_aggregates_runs(self):
        verifier = Path(__file__).resolve().parents[1] / "verify_release_runtime.py"
        source = verifier.read_text(encoding="utf-8")
        for class_name in (
            "NativeApiFixtureTest",
            "NativeLegalViewportTest",
            "NativeLoginNavigationTest",
            "NativeNotificationRuntimeAuditTest",
            "ReleaseSecurityRuntimeTest",
            "WorldMapStrictModeTest",
        ):
            with self.subTest(class_name=class_name):
                self.assertIn(f"com.example.{class_name}", source)
        self.assertIn("sum(summaries) == REQUIRED_RUNTIME_TESTS", source)
        self.assertIn("statuses.count('1') == REQUIRED_RUNTIME_TESTS and statuses.count('0') == REQUIRED_RUNTIME_TESTS", source)
        self.assertIn("'tests':REQUIRED_RUNTIME_TESTS", source)

    def test_rejects_failed_or_skipped_runtime_evidence(self):
        with self.assertRaisesRegex(ValueError, "zero failures"):
            validate_runtime_summary(self.summary(failures=1))
        with self.assertRaisesRegex(ValueError, "zero skipped tests"):
            validate_runtime_summary(self.summary(skipped=1))


if __name__ == "__main__":
    unittest.main()
