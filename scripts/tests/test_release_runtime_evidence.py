import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_runtime_evidence import REQUIRED_RUNTIME_TESTS, validate_runtime_summary


class ReleaseRuntimeEvidenceTest(unittest.TestCase):
    def summary(self, **updates):
        value = {"tests": REQUIRED_RUNTIME_TESTS, "failures": 0, "skipped": 0}
        value.update(updates)
        return value

    def test_accepts_current_complete_runtime_evidence(self):
        validate_runtime_summary(self.summary(tests=17))

    def test_rejects_old_runtime_test_count(self):
        for old_count in (10, 12):
            with self.subTest(old_count=old_count):
                with self.assertRaisesRegex(ValueError, "exactly 17 tests"):
                    validate_runtime_summary(self.summary(tests=old_count))

    def test_rejects_failed_or_skipped_runtime_evidence(self):
        with self.assertRaisesRegex(ValueError, "zero failures"):
            validate_runtime_summary(self.summary(failures=1))
        with self.assertRaisesRegex(ValueError, "zero skipped tests"):
            validate_runtime_summary(self.summary(skipped=1))


if __name__ == "__main__":
    unittest.main()
