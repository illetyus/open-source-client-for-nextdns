package com.example

import android.os.Bundle
import android.os.StrictMode
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawn
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.data.api.NextDnsApiService
import com.example.data.api.NextDnsNetworkClient
import com.example.data.api.NextDnsTestResponse
import com.example.data.api.NextDnsTestService
import com.example.data.local.NextDnsPreferences
import com.example.data.legal.LegalAcceptanceStore
import com.example.data.model.LogsHistoryFilter
import com.example.data.model.LogsHistoryPreset
import com.example.data.model.LogsSort
import com.example.data.model.LogsTimeWindow
import com.example.data.repository.NextDnsRepository
import com.example.data.security.ApiKeyProtector
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LocalNextDnsFaviconLoader
import com.example.ui.screens.NextDnsFaviconLoader
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.NextDnsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant

/** Real Compose screens backed only by deterministic, local synthetic HTTPS data. */
class BenchmarkFixtureActivity : ComponentActivity() {
  private var frameRecorder: com.example.performance.BenchmarkFrameRecorder? = null
  private lateinit var fixtureFaviconLoader: NextDnsFaviconLoader

  override fun onResume() {
    super.onResume()
    if (!intent.getBooleanExtra("benchmark_diagnostics", false)) return
    val capture = intent.getStringExtra("benchmark_capture") ?: return
    val scenario = intent.getStringExtra("benchmark_scenario") ?: return
    val safeLabel = Regex("[A-Za-z0-9_-]{1,96}")
    require(safeLabel.matches(capture) && safeLabel.matches(scenario))
    window.decorView.post {
      if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && frameRecorder == null) {
        frameRecorder = com.example.performance.BenchmarkFrameRecorder(this, capture, scenario)
      }
    }
  }

  override fun onStop() {
    frameRecorder?.stopAndWrite()
    frameRecorder = null
    super.onStop()
  }

  @OptIn(ExperimentalComposeUiApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    StrictMode.setThreadPolicy(
      StrictMode.ThreadPolicy.Builder()
        .detectDiskReads()
        .detectDiskWrites()
        .detectNetwork()
        .penaltyLog()
        .build()
    )

    val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)
      ?.takeIf { it.length <= MAX_BASE_URL_LENGTH }
      ?.toHttpUrlOrNull()
      ?.takeIf { it.isHttps && it.host in LOOPBACK_HOSTS && it.encodedPath == "/" && it.encodedQuery == null }
    val trustedCertificate = intent.getStringExtra(EXTRA_CERTIFICATE_BASE64)
      ?.takeIf { it.length <= MAX_CERTIFICATE_BASE64_LENGTH }
    if (baseUrl == null || trustedCertificate.isNullOrBlank()) {
      android.util.Log.e(TAG, "Synthetic benchmark fixture requires a loopback HTTPS endpoint and certificate")
      finish()
      return
    }

    lifecycleScope.launch {
      runCatching { withContext(Dispatchers.IO) { createFixtureRepository(baseUrl, trustedCertificate) } }
        .onSuccess { repository ->
          val viewModel = createFixtureViewModel(repository)
          setContent {
            AppTheme {
              ReportDrawn()
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .semantics { testTagsAsResourceId = true }
              ) {
                CompositionLocalProvider(LocalNextDnsFaviconLoader provides fixtureFaviconLoader) {
                  HomeScreen(viewModel = viewModel)
                }
              }
            }
          }
        }
        .onFailure { error ->
          android.util.Log.e(TAG, "Synthetic benchmark fixture failed to start", error)
          finish()
        }
    }
  }

  private suspend fun createFixtureRepository(
    baseUrl: HttpUrl,
    certificateBase64: String
  ): NextDnsRepository {
    val certificate = Base64.decode(certificateBase64, Base64.NO_WRAP).let { encoded ->
      ByteArrayInputStream(encoded).use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
      }
    }
    val clientCertificates = HandshakeCertificates.Builder()
      .addTrustedCertificate(certificate)
      .build()

    val fixtureClient = OkHttpClient.Builder()
      .addInterceptor { chain ->
        val requestUrl = chain.request().url
        if (!(requestUrl.isHttps && requestUrl.host == baseUrl.host && requestUrl.port == baseUrl.port)) {
          throw IOException("Synthetic benchmark rejects non-fixture endpoints")
        }
        chain.proceed(chain.request())
      }
      .followRedirects(false)
      .followSslRedirects(false)
      .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
      .build()
    val api = Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(fixtureClient)
      .addConverterFactory(MoshiConverterFactory.create(NextDnsNetworkClient.moshi))
      .build()
      .create(NextDnsApiService::class.java)
    fixtureFaviconLoader = NextDnsFaviconLoader(
      client = fixtureClient,
      urlsForDomain = { domain ->
        // Deliberately local to this source set: production still rejects .invalid.
        val key = domain.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        listOf("3x", "2x", "1x").map { scale -> baseUrl.resolve("favicons/$key@$scale.png")!!.toString() }
      },
      allowedHost = baseUrl.host
    )
    val diagnosticService = object : NextDnsTestService {
      override suspend fun testConnection(): Response<NextDnsTestResponse> = Response.success(
        NextDnsTestResponse(
          status = "ok",
          protocol = "DOH",
          profile = PROFILE_ID,
          resolver = "synthetic-fixture",
          server = "benchmark-only",
          anycast = true
        )
      )
    }

    val context = applicationContext
    check(LegalAcceptanceStore(context).acceptCurrentTerms()) {
      "Isolated benchmark package could not store its draft test acceptance"
    }
    val preferences = NextDnsPreferences(context, BenchmarkOnlyKeyProtector())
    val repository = NextDnsRepository(
      preferences = preferences,
      apiService = api,
      diagnosticService = diagnosticService,
      diagnosticProbe = { _ ->
        NextDnsTestResponse(
          status = "ok",
          protocol = "DOH",
          profile = PROFILE_ID,
          resolver = "synthetic-fixture",
          server = "benchmark-only",
          anycast = true
        )
      },
      downloadClient = fixtureClient,
      streamClient = fixtureClient,
      catalogCallFactory = fixtureClient,
      backgroundWorkEnabled = false
    )
    check(repository.loginWithApiKey(SYNTHETIC_API_KEY).isSuccess) {
      "Synthetic API login failed"
    }
    val to = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
    check(repository.setLogsHistoryFilter(
      LogsHistoryFilter(
        window = LogsTimeWindow(fromInclusive = to.minus(Duration.ofHours(24)), toExclusive = to),
        sort = LogsSort.NEWEST_FIRST,
        preset = LogsHistoryPreset.LAST_24_HOURS
      )
    )) { "Synthetic bounded history filter could not be applied" }
    return repository
  }

  private fun createFixtureViewModel(repository: NextDnsRepository): NextDnsViewModel {
    val factory = object : ViewModelProvider.Factory {
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NextDnsViewModel::class.java)) {
          "Benchmark fixture only provides NextDnsViewModel"
        }
        @Suppress("UNCHECKED_CAST")
        return NextDnsViewModel(repository) as T
      }
    }
    return ViewModelProvider(this, factory)[NextDnsViewModel::class.java]
  }

  companion object {
    private const val TAG = "BenchmarkFixture"
    private const val PROFILE_ID = "b00b1e"
    private const val SYNTHETIC_API_KEY = "benchmark-synthetic-key"
    private const val EXTRA_BASE_URL = "benchmark_base_url"
    private const val EXTRA_CERTIFICATE_BASE64 = "benchmark_certificate_base64"
    private const val MAX_BASE_URL_LENGTH = 2_048
    private const val MAX_CERTIFICATE_BASE64_LENGTH = 16_384
    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")
  }

  /** A marker-only protector for the synthetic benchmark credential in this isolated app id. */
  private class BenchmarkOnlyKeyProtector : ApiKeyProtector {
    override fun encrypt(plainText: String): String = "benchmark-only:$plainText"

    override fun decrypt(envelope: String): String {
      require(envelope.startsWith("benchmark-only:"))
      return envelope.removePrefix("benchmark-only:")
    }
  }
}
