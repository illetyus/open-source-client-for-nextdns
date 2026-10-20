import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_quality_regressions import assert_reports_cover_changed_sources, source_tree_sha256

TEST_TEMP_ROOT = Path(__file__).resolve().parents[2] / "build" / "quality-script-tests"


class QualityRegressionEvidenceTest(unittest.TestCase):
    def make_temporary_directory(self):
        TEST_TEMP_ROOT.mkdir(parents=True, exist_ok=True)
        return tempfile.TemporaryDirectory(dir=TEST_TEMP_ROOT)

    def test_old_reports_are_rejected_for_modified_sources(self):
        with self.make_temporary_directory() as temporary:
            root = Path(temporary)
            source = root / "app/src/main/Changed.kt"
            report = root / "app/build/test-results/testDebugUnitTest/TEST-suite.xml"
            source.parent.mkdir(parents=True)
            report.parent.mkdir(parents=True)
            report.write_text("<testsuite/>", encoding="utf-8")
            source.write_text("class Changed", encoding="utf-8")

            with self.assertRaisesRegex(AssertionError, "reports predate changed app sources"):
                assert_reports_cover_changed_sources(root, [report], ["app/src/main/Changed.kt"])

    def test_reports_after_source_changes_are_accepted(self):
        with self.make_temporary_directory() as temporary:
            root = Path(temporary)
            source = root / "app/src/main/Changed.kt"
            report = root / "app/build/test-results/testDebugUnitTest/TEST-suite.xml"
            source.parent.mkdir(parents=True)
            report.parent.mkdir(parents=True)
            source.write_text("class Changed", encoding="utf-8")
            report.write_text("<testsuite/>", encoding="utf-8")
            report.touch()

            assert_reports_cover_changed_sources(root, [report], ["app/src/main/Changed.kt"])

    def test_source_fingerprint_changes_when_source_content_changes(self):
        with self.make_temporary_directory() as temporary:
            root = Path(temporary)
            source = root / "app/src/main/Changed.kt"
            source.parent.mkdir(parents=True)
            source.write_text("class Before", encoding="utf-8")
            before = source_tree_sha256(root)
            source.write_text("class After", encoding="utf-8")

            self.assertNotEqual(before, source_tree_sha256(root))


if __name__ == "__main__":
    unittest.main()
