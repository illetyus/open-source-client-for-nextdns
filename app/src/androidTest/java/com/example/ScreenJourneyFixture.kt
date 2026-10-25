package com.example

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.example.data.api.NextDnsApiService
import com.example.data.api.NextDnsNetworkClient
import com.example.data.api.NextDnsTestService
import com.example.data.local.NextDnsPreferences
import com.example.data.repository.NextDnsRepository
import com.example.ui.viewmodel.NextDnsViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Isolated, synthetic account. Every fixture transport is constrained to localhost. */
internal class ScreenJourneyFixture(context: Context) : AutoCloseable {
  data class Call(val method: String, val path: String, val query: String, val body: String)
  val calls = CopyOnWriteArrayList<Call>()
  @Volatile var rejectSecurityMutation = false
  @Volatile var googleSafeBrowsing = true
  private val deny = linkedSetOf("blocked.fixture.example")
  private val allow = linkedSetOf("allowed.fixture.example")
  private val server = MockWebServer()
  private val logTimestamp = Instant.now().minusSeconds(3_600).toString()
  private val preferences = NextDnsPreferences(object : ContextWrapper(context) {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
      context.getSharedPreferences("screen_journey_$name", mode)
  })
  val repository: NextDnsRepository
  val model: NextDnsViewModel

  init {
    preferences.clear()
    val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
    val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
    val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
    server.useHttps(serverTls.sslSocketFactory(), false)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.requestUrl!!
        val body = request.body.readUtf8()
        calls += Call(request.method.orEmpty(), url.encodedPath, url.encodedQuery.orEmpty(), body)
        return respond(request, body)
      }
    }
    server.start()
    val client = OkHttpClient.Builder()
      .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
      .callTimeout(15, TimeUnit.SECONDS)
      .addInterceptor { chain ->
        // Catalog fallback URLs are also redirected; a synthetic key cannot leave the device.
        val original = chain.request()
        val local = server.url(original.url.encodedPath).newBuilder()
          .encodedQuery(original.url.encodedQuery).build()
        chain.proceed(original.newBuilder().url(local).build())
      }.build()
    val streamClient = OkHttpClient.Builder().addInterceptor { chain ->
      Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
        .code(200).message("Synthetic empty stream")
        .body("".toResponseBody("text/event-stream".toMediaType())).build()
    }.build()
    val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
      .client(client).addConverterFactory(MoshiConverterFactory.create(NextDnsNetworkClient.moshi)).build()
    repository = NextDnsRepository(
      preferences = preferences,
      apiService = retrofit.create(NextDnsApiService::class.java),
      diagnosticService = retrofit.create(NextDnsTestService::class.java),
      diagnosticProbe = { null }, downloadClient = client, streamClient = streamClient,
      backgroundWorkEnabled = false, catalogCallFactory = client
    )
    check(runBlocking { repository.loginWithApiKey("SYNTHETIC_SCREEN_JOURNEY_KEY") }.isSuccess)
    model = NextDnsViewModel(repository)
  }

  private fun json(data: String, code: Int = 200) = MockResponse().setResponseCode(code)
    .setHeader("Content-Type", "application/json").setBody(data)

  private fun respond(request: RecordedRequest, body: String): MockResponse {
    val url = request.requestUrl!!
    val path = url.encodedPath
    if (path == "/profiles") return json("""{"data":[{"id":"aaaaaa","name":"Synthetic UI profile","fingerprint":"fp0123456789abcdef"}]}""")
    if (path == "/profiles/aaaaaa/security") {
      if (request.method == "PATCH") {
        if (rejectSecurityMutation) return json("""{"errors":[{"code":"invalid","detail":"Synthetic mutation rejected"}]}""", 503)
        val update = JSONObject(body)
        if (update.has("googleSafeBrowsing")) googleSafeBrowsing = update.getBoolean("googleSafeBrowsing")
      }
      return json("""{"data":{"googleSafeBrowsing":$googleSafeBrowsing}}""")
    }
    for ((section, rules) in listOf("denylist" to deny, "allowlist" to allow)) {
      val base = "/profiles/aaaaaa/$section"
      if (path == base || path.startsWith("$base/")) {
        synchronized(rules) {
          when (request.method) {
            "POST" -> rules += JSONObject(body).getString("id")
            "DELETE" -> rules.remove(url.pathSegments.last())
          }
          return json(JSONObject().put("data", JSONArray(rules.map {
            JSONObject().put("id", it).put("active", true)
          })).toString())
        }
      }
    }
    if (path == "/profiles/aaaaaa/logs") {
      val rows = listOf(
        JSONObject().put("id", "blocked-log").put("timestamp", logTimestamp)
          .put("domain", "blocked.fixture.example").put("status", "blocked")
          .put("device", JSONObject().put("id", "phone").put("name", "Fixture phone")),
        JSONObject().put("id", "allowed-log").put("timestamp", logTimestamp)
          .put("domain", "allowed.fixture.example").put("status", "default")
      ).filter { row ->
        val device = url.queryParameter("device")
        val status = url.queryParameter("status")
        val search = url.queryParameter("search")
        val from = url.queryParameter("from")?.let(Instant::parse)
        val to = url.queryParameter("to")?.let(Instant::parse)
        val timestamp = Instant.parse(row.getString("timestamp"))
        (device == null || row.optJSONObject("device")?.optString("id") == device) &&
          (status == null || row.optString("status") == status) &&
          (search == null || row.optString("domain").contains(search)) &&
          (from == null || !timestamp.isBefore(from)) && (to == null || timestamp.isBefore(to))
      }
      return json(JSONObject().put("data", JSONArray(rows)).toString())
    }
    if (path.startsWith("/profiles/aaaaaa/analytics/")) {
      if (url.queryParameter("series") == "true") return json("""{"data":[],"meta":{"times":[],"interval":3600}}""")
      return when (path.substringAfterLast('/')) {
        "status" -> json("""{"data":[{"status":"default","queries":20},{"status":"blocked","queries":5}]}""")
        "devices" -> json("""{"data":[{"id":"phone","name":"Fixture phone","queries":25}]}""")
        "domains" -> json("""{"data":[{"domain":"allowed.fixture.example","queries":20}]}""")
        "ips" -> json("""{"data":[{"ip":"203.0.113.25","queries":25}]}""")
        else -> json("""{"data":[]}""")
      }
    }
    return when (path) {
      "/profiles/aaaaaa/settings" -> json("""{"data":{"logs":{"enabled":true,"retention":604800,"location":"eu"}}}""")
      "/profiles/aaaaaa/privacy", "/profiles/aaaaaa/parentalControl" -> json("""{"data":{}}""")
      "/profiles/aaaaaa/privacy/blocklists", "/profiles/aaaaaa/privacy/natives",
      "/privacy/blocklists", "/privacy/natives", "/parentalControl/services",
      "/parentalControl/categories", "/security/tlds" -> json("""{"data":[]}""")
      "/profiles/aaaaaa" -> json("""{"data":{"id":"aaaaaa","name":"Synthetic UI profile"}}""")
      else -> json("""{"errors":[{"code":"not_found","detail":"Unimplemented synthetic route"}]}""", 404)
    }
  }

  override fun close() {
    runBlocking { repository.logout() }
    preferences.clear()
    server.shutdown()
  }
}
