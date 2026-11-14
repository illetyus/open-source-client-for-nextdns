import tempfile
import unittest
from pathlib import Path
import sys
import os

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_quality_regressions as quality
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
            os.utime(report, (1_700_000_000, 1_700_000_000))
            os.utime(source, (1_700_000_002, 1_700_000_002))

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
            os.utime(source, (1_700_000_000, 1_700_000_000))
            os.utime(report, (1_700_000_002, 1_700_000_002))

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

    def test_retired_speed_regression_needs_removal_evidence(self):
        required = quality.REQUIRED
        account_case = ("com.example.audit.AccountSettingsFlowTest", "accountShortcutAndSeparateConnectionPageAreRemoved")
        preferences_class = "com.example.data.local.RetiredFeaturePreferencesTest"
        self.assertIn(account_case, required["NDNS-061"])
        self.assertIn((preferences_class, "retiredSpeedSettingsAreClearedWithoutTouchingCredentialsThemeLocaleOrLegalAcceptance"), required["NDNS-061"])
        self.assertIn((preferences_class, "retiredSpeedSettingsCleanupIsIdempotent"), required["NDNS-062"])
        retired_classes = {
            class_name
            for cases in (required["NDNS-061"], required["NDNS-062"])
            for class_name, _ in cases
        }
        self.assertFalse(retired_classes.intersection({
            "com.example.data.diagnostics.LibreSpeedContractTest",
            "com.example.ui.screens.SpeedTestLifecycleTest",
        }))

    def test_removed_link_ip_flow_uses_absence_regression(self):
        required = quality.REQUIRED["NDNS-007"]
        self.assertNotIn(
            ("com.example.data.api.CancellableCallTest", "ipLinkSuccessClosesUnconsumedBody"),
            required,
        )
        self.assertIn(
            ("com.example.data.api.NextDnsApiContractTest", "undocumentedSetupAndLinkedIpRoutesAreNotDeclared"),
            required,
        )

    def test_profile_ipv6_regression_uses_current_validated_profile_cases(self):
        required = quality.REQUIRED["NDNS-013"]
        self.assertEqual(
            {
                ("com.example.audit.UiAuditTest", "endpointsMustUseTheValidatedProfileIpv6Format"),
                ("com.example.audit.UiAuditTest", "copiedIpv6MustMatchBothDisplayedServiceAddresses"),
                ("com.example.audit.InputAndIdentityTest", "profileIpv6EndpointsComeFromTheValidatedProfileId"),
            },
            set(required),
        )

    def test_device_inventory_pagination_regressions_are_required(self):
        repository = "com.example.audit.RepositoryAuditTest"
        required = quality.REQUIRED["NDNS-073"]
        self.assertEqual({
            (repository, "analyticsDevicePaginationIncludesEveryFilterableDevice"),
            (repository, "repeatedAnalyticsDeviceCursorKeepsTheLastCompleteInventory"),
            (repository, "analyticsDevicePageLimitKeepsTheLastCompleteInventory"),
        }, set(required))


    def test_analytics_time_filter_values_are_stable_and_localized(self):
        repository = Path(__file__).resolve().parents[2]
        screen = (repository / "app/src/main/java/com/example/ui/screens/AnalyticsScreen.kt").read_text(encoding="utf-8")
        self.assertTrue('listOf("24h", "7d", "30d", "90d")' in screen, "period filters must use stable API keys")
        self.assertIn('mutableStateOf("24h")', screen)
        self.assertIn('R.string.last_24_hours', screen)
        self.assertIn('R.string.last_7_days', screen)
        self.assertIn('R.string.last_30_days', screen)
        self.assertIn('R.string.last_3_months', screen)
        self.assertNotIn('listOf("Son 24 saat"', screen)

        for locale in ("values", "values-tr", "values-de", "values-fr", "values-es"):
            strings = (repository / f"app/src/main/res/{locale}/strings.xml").read_text(encoding="utf-8")
            for resource in ("last_24_hours", "last_7_days", "last_30_days", "last_3_months"):
                self.assertIn(f'<string name="{resource}">', strings, f"{locale} is missing {resource}")

if __name__ == "__main__":
    unittest.main()
