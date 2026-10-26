import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release_dex_audit as audit


class RetiredFeatureDexAuditTest(unittest.TestCase):
    @patch.object(audit, "type_descriptors", return_value={b"[La/b;"})
    def test_obfuscated_fixture_reference_is_still_rejected(self, _):
        mapping = "com.example.BenchmarkFixtureActivity -> a.b:\n    1:1:void onCreate():1:1 -> c\n"
        with self.assertRaisesRegex(audit.TestCodeFound, "Benchmark fixture type"):
            audit.audit_dex(b"synthetic dex", mapping=mapping)

    @patch.object(audit, "type_descriptors", return_value={b"La/b;"})
    def test_normal_obfuscated_production_class_is_allowed(self, _):
        self.assertEqual(1, audit.audit_dex(b"synthetic dex", mapping="com.example.MainActivity -> a.b:\n"))

    @patch.object(audit, "type_descriptors", return_value={b"La/b;"})
    def test_merged_class_mapping_does_not_hide_a_fixture(self, _):
        with self.assertRaisesRegex(audit.TestCodeFound, "Benchmark fixture type"):
            audit.audit_dex(b"synthetic dex", mapping="one.Name -> a.b:\ncom.example.BenchmarkFixtureActivity -> a.b:\n")

    @patch.object(audit, "type_descriptors", return_value={b"La/b;"})
    def test_inlined_fixture_origin_without_class_header_is_rejected(self, _):
        mapping = ("com.example.MainActivity -> a.b:\n"
            "    1:2:void com.example.BenchmarkFixtureActivity.onCreate(android.os.Bundle):3:4 -> c\n")
        with self.assertRaisesRegex(audit.TestCodeFound, "Benchmark fixture type"):
            audit.audit_dex(b"synthetic dex", mapping=mapping)

    @patch.object(audit, "type_descriptors", return_value={
        b"Lcom/example/data/diagnostics/LibreSpeedClient;"
    })
    def test_release_rejects_retired_speed_client_type(self, _):
        with self.assertRaisesRegex(audit.TestCodeFound, "Retired product type"):
            audit.audit_dex(b"synthetic dex")

    @patch.object(audit, "type_descriptors", return_value={
        b"Lcom/example/ui/screens/SpeedTestDialogKt;"
    })
    def test_release_rejects_retired_speed_dialog_type(self, _):
        with self.assertRaisesRegex(audit.TestCodeFound, "Retired product type"):
            audit.audit_dex(b"synthetic dex")

    @patch.object(audit, "type_descriptors", return_value={
        b"Lcom/example/BenchmarkFixtureActivity$BenchmarkApiDispatcher;"
    })
    def test_release_rejects_performance_benchmark_fixture_types(self, _):
        with self.assertRaisesRegex(audit.TestCodeFound, "Benchmark fixture type"):
            audit.audit_dex(b"synthetic dex")

    @patch.object(audit, "type_descriptors", return_value={b"Lcom/example/MainActivity;"})
    def test_release_allows_cleanup_preference_name_without_speed_backend(self, _):
        self.assertEqual(1, audit.audit_dex(b"speed_test_local_settings"))

    @patch.object(audit, "type_descriptors", return_value={b"Lcom/example/MainActivity;"})
    def test_release_rejects_retired_speed_backend_markers(self, _):
        for marker in (b"garbage.php", b"empty.php"):
            with self.subTest(marker=marker):
                with self.assertRaisesRegex(audit.TestCodeFound, "Forbidden release marker"):
                    audit.audit_dex(marker)


if __name__ == "__main__":
    unittest.main()
