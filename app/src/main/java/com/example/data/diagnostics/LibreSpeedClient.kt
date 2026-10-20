package com.example.data.diagnostics

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

internal enum class SpeedTestConfigError {
  INVALID_SERVER_URL,
  HTTPS_REQUIRED,
  CREDENTIALS_NOT_ALLOWED,
  SERVER_URL_QUERY_NOT_ALLOWED,
  INVALID_PRIVACY_URL,
  INVALID_BACKEND_PATH,
  AUTHORIZATION_REQUIRED
}

internal data class SpeedTestServerValidation(
  val server: ValidatedLibreSpeedServer? = null,
  val error: SpeedTestConfigError? = null
)

internal data class ValidatedLibreSpeedServer internal constructor(
  val baseUrl: HttpUrl,
  val privacyPolicyUrl: HttpUrl,
  val backendPath: String,
  val pingUrl: HttpUrl,
  val downloadUrl: HttpUrl,
  val uploadUrl: HttpUrl
) {
  val serverLabel: String get() = baseUrl.host
}

internal fun validateLibreSpeedServer(
  baseUrl: String,
  privacyPolicyUrl: String,
  backendPath: String,
  authorizationConfirmed: Boolean,
  allowHttpForTests: Boolean = false
): SpeedTestServerValidation {
  val parsedBaseUrl = baseUrl.trim().toHttpUrlOrNull()
    ?: return SpeedTestServerValidation(error = SpeedTestConfigError.INVALID_SERVER_URL)
  if (parsedBaseUrl.scheme != "https" && !(allowHttpForTests && parsedBaseUrl.scheme == "http")) {
    return SpeedTestServerValidation(error = SpeedTestConfigError.HTTPS_REQUIRED)
  }
  if (parsedBaseUrl.username.isNotEmpty() || parsedBaseUrl.password.isNotEmpty()) {
    return SpeedTestServerValidation(error = SpeedTestConfigError.CREDENTIALS_NOT_ALLOWED)
  }
  if (parsedBaseUrl.query != null || parsedBaseUrl.fragment != null) {
    return SpeedTestServerValidation(error = SpeedTestConfigError.SERVER_URL_QUERY_NOT_ALLOWED)
  }

  val parsedPolicyUrl = privacyPolicyUrl.trim().toHttpUrlOrNull()
    ?: return SpeedTestServerValidation(error = SpeedTestConfigError.INVALID_PRIVACY_URL)
  if (parsedPolicyUrl.scheme != "https" || parsedPolicyUrl.username.isNotEmpty() ||
    parsedPolicyUrl.password.isNotEmpty() || parsedPolicyUrl.query != null || parsedPolicyUrl.fragment != null) {
    return SpeedTestServerValidation(error = SpeedTestConfigError.INVALID_PRIVACY_URL)
  }

  val normalizedBackendPath = backendPath.trim().trim('/')
  val backendSegments = if (normalizedBackendPath.isEmpty()) {
    emptyList()
  } else {
    normalizedBackendPath.split('/')
  }
  if (backendSegments.any { segment ->
      segment.isBlank() || segment == "." || segment == ".." ||
        segment.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-_~.") }
    }) {
    return SpeedTestServerValidation(error = SpeedTestConfigError.INVALID_BACKEND_PATH)
  }

  val normalizedBase = parsedBaseUrl.newBuilder()
    .encodedPath(parsedBaseUrl.encodedPath.let { if (it.endsWith('/')) it else "$it/" })
    .build()
  fun endpoint(fileName: String): HttpUrl = normalizedBase.newBuilder().apply {
    backendSegments.forEach { addPathSegment(it) }
    addPathSegment(fileName)
  }.build()

  val server = ValidatedLibreSpeedServer(
      baseUrl = normalizedBase,
      privacyPolicyUrl = parsedPolicyUrl,
      backendPath = normalizedBackendPath,
      pingUrl = endpoint("empty.php"),
      downloadUrl = endpoint("garbage.php"),
      uploadUrl = endpoint("empty.php")
    )
  return SpeedTestServerValidation(
    server = server,
    error = if (authorizationConfirmed) null else SpeedTestConfigError.AUTHORIZATION_REQUIRED
  )
}

internal data class SpeedTestPingResult(
  val latencyMs: Double,
  val jitterMs: Double,
  val samplesMs: List<Double>
)

internal data class SpeedTestTransferResult(
  val megabitsPerSecond: Double,
  val bytesTransferred: Long,
  val durationMs: Double
)

/**
 * Small native client for the documented LibreSpeed PHP backend paths.
 * It never chooses a server, performs IP/ISP lookup, sends telemetry, or saves results.
 */
@OptIn(InternalCoroutinesApi::class)
internal class LibreSpeedClient(
  httpClient: OkHttpClient = OkHttpClient(),
  private val allowHttpForTests: Boolean = false,
  private val nanoTime: () -> Long = System::nanoTime
) {
  private val client = httpClient.newBuilder()
    .cookieJar(CookieJar.NO_COOKIES)
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

  suspend fun measurePing(
    server: ValidatedLibreSpeedServer,
    samples: Int = DEFAULT_PING_SAMPLES
  ): SpeedTestPingResult {
    require(samples in 1..MAX_PING_SAMPLES)
    val durations = ArrayList<Double>(samples)
    repeat(samples) {
      val startedAt = nanoTime()
      execute(request(server.pingUrl)) { response ->
        validateResponse(response, server, server.pingUrl)
      }
      durations += elapsedMillis(startedAt, nanoTime())
    }
    val average = durations.average()
    val jitter = durations.zipWithNext { first, next -> kotlin.math.abs(next - first) }
      .average()
      .takeIf { it.isFinite() }
      ?: 0.0
    return SpeedTestPingResult(average, jitter, durations)
  }

  suspend fun measureDownload(
    server: ValidatedLibreSpeedServer,
    maxBytes: Long = DEFAULT_DOWNLOAD_BYTES,
    onProgress: (transferredBytes: Long, maxBytes: Long) -> Unit = { _, _ -> }
  ): SpeedTestTransferResult {
    require(maxBytes in MIN_DOWNLOAD_BYTES..MAX_DOWNLOAD_BYTES)
    val requestedMebibytes = (maxBytes / BYTES_PER_MEBIBYTE)
      .coerceIn(MIN_DOWNLOAD_MEBIBYTES, MAX_DOWNLOAD_MEBIBYTES)
    val requestedBytes = requestedMebibytes * BYTES_PER_MEBIBYTE
    val url = server.downloadUrl.newBuilder()
      // LibreSpeed's garbage.php accepts the requested download size in MiB (4–1024),
      // while this client caps actual bytes at 10,000,000 to stay below 10 decimal MB.
      .addQueryParameter("ckSize", requestedMebibytes.toString())
      .build()
    val startedAt = nanoTime()
    val transferred = execute(request(url)) { response ->
      validateResponse(response, server, url)
      val body = response.body ?: throw IOException("Missing download body")
      val mediaType = body.contentType()?.let { "${it.type}/${it.subtype}" }
      if (!mediaType.equals(DOWNLOAD_MEDIA_TYPE, ignoreCase = true)) {
        throw IOException("The configured speed-test server returned an unexpected media type")
      }
      if (body.contentLength() > maxBytes) throw IOException("Download exceeded the configured limit")
      if (body.contentLength() >= 0 && body.contentLength() != requestedBytes) {
        throw IOException("The configured speed-test server returned an incomplete payload")
      }

      body.byteStream().use { input ->
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
          val allowedRead = minOf(buffer.size.toLong(), maxBytes - total + 1).toInt()
          val read = input.read(buffer, 0, allowedRead)
          if (read < 0) break
          total += read
          if (total > maxBytes) throw IOException("Download exceeded the configured limit")
          onProgress(total, maxBytes)
        }
        total
      }
    }
    if (transferred != requestedBytes) throw IOException("The configured speed-test server returned an incomplete payload")
    return transferResult(transferred, startedAt, nanoTime())
  }

  suspend fun measureUpload(
    server: ValidatedLibreSpeedServer,
    bytesToSend: Int = DEFAULT_UPLOAD_BYTES,
    onProgress: (transferredBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
  ): SpeedTestTransferResult {
    require(bytesToSend in 1..MAX_UPLOAD_BYTES)
    val startedAt = nanoTime()
    val body = SyntheticUploadBody(bytesToSend, onProgress)
    execute(request(server.uploadUrl, body)) { response ->
      validateResponse(response, server, server.uploadUrl)
    }
    return transferResult(bytesToSend.toLong(), startedAt, nanoTime())
  }

  private suspend fun <T : Any> execute(request: Request, read: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
      val call = client.newCall(request)
      continuation.invokeOnCancellation { call.cancel() }
      call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          resumeFailure(continuation, e)
        }

        override fun onResponse(call: Call, response: Response) {
          val result = try {
            response.use(read)
          } catch (error: Exception) {
            resumeFailure(continuation, error)
            return
          }
          resumeSuccess(continuation, result)
        }
      })
    }

  private fun validateResponse(
    response: Response,
    server: ValidatedLibreSpeedServer,
    requestedUrl: HttpUrl
  ) {
    if (!response.isSuccessful || response.priorResponse != null) {
      throw IOException("The configured speed-test server rejected the request")
    }
    val responseUrl = response.request.url
    val permittedScheme = responseUrl.isHttps || (allowHttpForTests && responseUrl.scheme == "http")
    if (!permittedScheme || responseUrl.host != server.baseUrl.host ||
      responseUrl.port != server.baseUrl.port || responseUrl != requestedUrl) {
      throw IOException("The speed-test response did not match the configured server")
    }
  }

  private fun request(url: HttpUrl, body: RequestBody? = null): Request {
    if (!url.isHttps && !(allowHttpForTests && url.scheme == "http")) {
      throw IOException("HTTPS is required for speed-test traffic")
    }
    return Request.Builder()
      .url(url)
      .header("Accept-Encoding", "identity")
      .header("Cache-Control", "no-store")
      .method(if (body == null) "GET" else "POST", body)
      .build()
  }

  private fun transferResult(bytes: Long, startedAt: Long, finishedAt: Long): SpeedTestTransferResult {
    val durationMs = elapsedMillis(startedAt, finishedAt)
    val megabitsPerSecond = bytes.toDouble() * 8.0 / (durationMs / 1_000.0) / 1_000_000.0
    return SpeedTestTransferResult(megabitsPerSecond, bytes, durationMs)
  }

  private fun elapsedMillis(startedAt: Long, finishedAt: Long): Double =
    ((finishedAt - startedAt).coerceAtLeast(1L)) / 1_000_000.0

  private class SyntheticUploadBody(
    private val byteCount: Int,
    private val onProgress: (Long, Long) -> Unit
  ) : RequestBody() {
    override fun contentType() = "application/octet-stream".toMediaType()
    override fun contentLength(): Long = byteCount.toLong()

    override fun writeTo(sink: BufferedSink) {
      val random = SecureRandom()
      val buffer = ByteArray(32 * 1024)
      var written = 0L
      while (written < byteCount) {
        val chunkSize = minOf(buffer.size.toLong(), byteCount - written).toInt()
        random.nextBytes(buffer)
        sink.write(buffer, 0, chunkSize)
        sink.flush()
        written += chunkSize
        onProgress(written, byteCount.toLong())
      }
    }
  }

  private fun <T : Any> resumeSuccess(continuation: CancellableContinuation<T>, result: T) {
    val token = continuation.tryResume(result) ?: return
    continuation.completeResume(token)
  }

  private fun <T : Any> resumeFailure(continuation: CancellableContinuation<T>, error: Throwable) {
    val token = continuation.tryResumeWithException(error) ?: return
    continuation.completeResume(token)
  }

  companion object {
    const val DEFAULT_PING_SAMPLES = 3
    const val MAX_PING_SAMPLES = 5
    const val DEFAULT_DOWNLOAD_BYTES = 10_000_000L
    const val MAX_DOWNLOAD_BYTES = 10_000_000L
    const val MIN_DOWNLOAD_BYTES = MIN_DOWNLOAD_MEBIBYTES * BYTES_PER_MEBIBYTE
    const val MIN_DOWNLOAD_MEBIBYTES = 4L
    const val MAX_DOWNLOAD_MEBIBYTES = 9L
    const val BYTES_PER_MEBIBYTE = 1_048_576L
    private const val DOWNLOAD_MEDIA_TYPE = "application/octet-stream"
    const val DEFAULT_UPLOAD_BYTES = 1_000_000
    const val MAX_UPLOAD_BYTES = 1_000_000
  }
}
