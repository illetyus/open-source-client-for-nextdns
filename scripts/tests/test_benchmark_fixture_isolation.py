from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class BenchmarkFixtureIsolationTest(unittest.TestCase):
    def test_fixture_routes_all_catalog_fallbacks_through_its_guarded_client(self):
        repository = (ROOT / 'app/src/main/java/com/example/data/repository/NextDnsRepository.kt').read_text(encoding='utf8')
        activity = (ROOT / 'app/src/benchmark/java/com/example/BenchmarkFixtureActivity.kt').read_text(encoding='utf8')
        self.assertIsNone(re.search(r'NextDnsNetworkClient\.fetchAvailable\w+Direct\(\)', repository), 'Catalog fallbacks bypass the injected factory')
        self.assertIn('catalogCallFactory = fixtureClient', activity)
        self.assertIn('requestUrl.host == baseUrl.host && requestUrl.port == baseUrl.port', activity)
        self.assertIn('throw IOException("Synthetic benchmark rejects non-fixture endpoints")', activity)

    def test_analytics_journey_scrolls_in_render_order(self):
        source = (ROOT / 'macrobenchmark/src/main/java/com/example/macrobenchmark/AuthenticatedClientBenchmark.kt').read_text(encoding='utf8')
        journey = source.split('fun analyticsSeriesAndInteractiveCountryMapRender()', 1)[1].split('private fun', 1)[0]
        map_position = journey.index('scrollUntilFound(By.res("world_country_map")')
        chart_scroll = 'scrollUntilFound(By.res("analytics_series_line_chart")'
        self.assertTrue(chart_scroll in journey, 'Offscreen chart must be reached by scrolling')
        chart_position = journey.index(chart_scroll)
        self.assertLess(map_position, chart_position)
        self.assertNotIn('Until.hasObject(By.res("analytics_series_line_chart"))', journey)

    def test_benchmark_fixture_is_confined_to_isolated_variant(self):
        app_gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf8')
        macrobenchmark_gradle = (ROOT / 'macrobenchmark/build.gradle.kts').read_text(encoding='utf8')
        main_manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf8')
        benchmark_manifest = ROOT / 'app/src/benchmark/AndroidManifest.xml'
        fixture_source = ROOT / 'app/src/benchmark/java/com/example/BenchmarkFixtureActivity.kt'
        api_fixture_source = ROOT / 'macrobenchmark/src/main/java/com/example/macrobenchmark/SyntheticNextDnsApi.kt'
        macrobenchmark_manifest = ROOT / 'macrobenchmark/src/main/AndroidManifest.xml'

        self.assertIn('applicationIdSuffix = ".benchmark"', app_gradle)
        self.assertNotIn('BenchmarkFixtureActivity', main_manifest)
        self.assertTrue(benchmark_manifest.is_file())
        self.assertTrue(fixture_source.is_file())
        self.assertTrue(api_fixture_source.is_file())
        self.assertIn('android.permission.INTERNET', macrobenchmark_manifest.read_text(encoding='utf8'))
        self.assertIn('implementation("com.squareup.okhttp3:mockwebserver:4.10.0")', macrobenchmark_gradle)

        macrobenchmark = (ROOT / 'macrobenchmark/src/main/java/com/example/macrobenchmark/AuthenticatedClientBenchmark.kt').read_text(encoding='utf8')
        api_fixture = api_fixture_source.read_text(encoding='utf8')
        self.assertIn('const val MEASURED_ITERATIONS = 10', macrobenchmark)
        self.assertIn('const val WARMUP_ITERATIONS = 2', macrobenchmark)
        self.assertIn('Intent.CATEGORY_LAUNCHER', api_fixture)
        self.assertIn('android.intent.category.LAUNCHER', api_fixture)
        startup_benchmark = (ROOT / 'macrobenchmark/src/main/java/com/example/macrobenchmark/ClientStartupBenchmark.kt').read_text(encoding='utf8')
        self.assertIn('WARMUP_ITERATIONS = 2', startup_benchmark)
        self.assertIn('MEASURED_ITERATIONS = 10', startup_benchmark)
        self.assertIn('tab_logs', macrobenchmark)
        self.assertIn('tab_analytics', macrobenchmark)
        self.assertIn('TwoHundredSyntheticRows', macrobenchmark)
        self.assertIn('site-199.invalid', macrobenchmark)
        self.assertIn('MAX_SCROLL_SWIPES = 80', macrobenchmark)
        self.assertIn('scrollUntilFound(By.res("logs_load_older")', macrobenchmark)
        self.assertIn('scrollUntilFound(By.textContains("site-199.invalid")', macrobenchmark)
        fixture = api_fixture
        self.assertIn('firstIndex + 100', fixture)
        self.assertIn('PAGE_TWO_CURSOR', fixture)
        self.assertIn('useHttps(serverCertificates.sslSocketFactory(), false)', fixture)
        self.assertIn('.heldCertificate(certificate)', fixture)
        self.assertIn('Base64.getEncoder().encodeToString(certificate.certificate.encoded)', fixture)
        activity = fixture_source.read_text(encoding='utf8')
        self.assertIn('baseUrl(baseUrl)', activity)
        self.assertIn('.followRedirects(false)', activity)
        self.assertIn('.followSslRedirects(false)', activity)
        self.assertIn('it.host in LOOPBACK_HOSTS', activity)
        self.assertIn('EXTRA_BASE_URL = "benchmark_base_url"', activity)
        self.assertIn('EXTRA_CERTIFICATE_BASE64 = "benchmark_certificate_base64"', activity)
        self.assertNotIn('privateKey', activity)
        self.assertNotIn('MockWebServer', activity)
        self.assertNotIn('api.nextdns.com', fixture + activity)
        self.assertIn('world_country_map', (ROOT / 'app/src/main/java/com/example/ui/screens/WorldMapChart.kt').read_text(encoding='utf8'))

    def test_fixture_tls_certificate_and_synthetic_key_are_test_only(self):
        app_gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf8')
        fixture_source = (ROOT / 'app/src/benchmark/java/com/example/BenchmarkFixtureActivity.kt').read_text(encoding='utf8')
        api_fixture = (ROOT / 'macrobenchmark/src/main/java/com/example/macrobenchmark/SyntheticNextDnsApi.kt').read_text(encoding='utf8')
        self.assertIn('benchmarkImplementation", "com.squareup.okhttp3:okhttp-tls', app_gradle)
        self.assertNotIn('benchmarkImplementation", "com.squareup.okhttp3:mockwebserver', app_gradle)
        self.assertIn('implementation("com.squareup.okhttp3:okhttp-tls:4.10.0")', (ROOT / 'macrobenchmark/build.gradle.kts').read_text(encoding='utf8'))
        self.assertNotIn('privateKeyPkcs8Pem', api_fixture)
        self.assertNotIn('AIza', fixture_source + api_fixture)
        self.assertNotIn('KEYSTORE_PATH', fixture_source + api_fixture)
        self.assertIn('backgroundWorkEnabled = false', fixture_source)
        repository = (ROOT / 'app/src/main/java/com/example/data/repository/NextDnsRepository.kt').read_text(encoding='utf8')
        self.assertRegex(
            repository,
            r'if \(backgroundWorkEnabled\)\s*\{\s*safeApiCall\("reconcileNotifications"\)'
        )

    def test_exported_fixture_bounds_caller_supplied_url_and_certificate(self):
        activity = (ROOT / 'app/src/benchmark/java/com/example/BenchmarkFixtureActivity.kt').read_text(encoding='utf8')
        self.assertIn('MAX_BASE_URL_LENGTH = 2_048', activity)
        self.assertIn('MAX_CERTIFICATE_BASE64_LENGTH = 16_384', activity)
        self.assertIn('it.length <= MAX_BASE_URL_LENGTH', activity)
        self.assertIn('it.length <= MAX_CERTIFICATE_BASE64_LENGTH', activity)


if __name__ == '__main__':
    unittest.main()
