package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.IDN
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request

private const val FAVICON_HOST = "favicons.nextdns.io"
private const val MAX_FAVICON_BYTES = 128 * 1024
private const val MAX_FAVICON_DIMENSION = 1024
private const val CACHED_FAVICON_DIMENSION = 192
private const val MAX_FAVICON_CACHE_BYTES = 4 * 1024 * 1024
internal const val FAVICON_TOTAL_TIMEOUT_MS = 8_000
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
private val LOCAL_ONLY_HOST_SUFFIXES = setOf(
  "localhost", "local", "internal", "lan", "test", "invalid", "example", "home.arpa",
  "nip.io", "sslip.io", "xip.io", "localtest.me", "lvh.me", "local.gd"
)

/**
 * Builds the observed NextDNS favicon URL only from a hostname. The route is
 * not part of the published NextDNS API, so callers must retain a local fallback.
 */
internal fun nextDnsFaviconUrl(domain: String): String? {
  val input = domain.trim()
  if (input.isEmpty() || input.any { it.isWhitespace() || it in "/:@?#\\*" }) return null

  val ascii = runCatching { IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES) }
    .getOrNull()
    ?.lowercase(Locale.ROOT)
    ?: return null
  val hostname = ascii.removeSuffix(".")
  if (hostname.length > 253 || !hostname.contains('.')) return null

  val labels = hostname.split('.')
  if (labels.any { label ->
      label.isEmpty() || label.length > 63 || label.startsWith('-') || label.endsWith('-') ||
        label.any { !(it in 'a'..'z' || it in '0'..'9' || it == '-') }
    }) return null
  if (hostname.matches(Regex("^[0-9.]+$"))) return null
  if (LOCAL_ONLY_HOST_SUFFIXES.any { hostname == it || hostname.endsWith(".$it") }) return null

  val hexHostname = hostname.toByteArray(StandardCharsets.US_ASCII)
    .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
  return "https://$FAVICON_HOST/hex:$hexHostname@2x.png"
}

internal object NextDnsFaviconHttp {
  val client = OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(3, TimeUnit.SECONDS)
    .callTimeout(FAVICON_TOTAL_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .build()
}

internal class NextDnsFaviconLoader(
  private val client: OkHttpClient = NextDnsFaviconHttp.client,
  private val urlForDomain: (String) -> String? = ::nextDnsFaviconUrl,
  private val allowedHost: String = FAVICON_HOST,
  private val allowHttpForTests: Boolean = false
) {
  private val cache = object : LruCache<String, Bitmap>(MAX_FAVICON_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
  }

  suspend fun load(domain: String): Bitmap? {
    val url = urlForDomain(domain) ?: return null
    cache.get(url)?.let { return it }
    val downloaded = download(url) ?: return null
    return cache.get(url) ?: cache.put(url, downloaded).let { previous ->
      previous ?: downloaded
    }
  }

  private suspend fun download(url: String): Bitmap? {
    val bytes = requestBytes(url) ?: return null
    return withContext(Dispatchers.IO) { decode(bytes) }
  }

  private suspend fun requestBytes(url: String): ByteArray? = suspendCancellableCoroutine { continuation ->
    val request = runCatching {
      Request.Builder()
        .url(url)
        .header("Accept", "image/png,image/*;q=0.8")
        .get()
        .build()
    }.getOrNull()
    if (request == null || request.url.host != allowedHost ||
      (!allowHttpForTests && !request.url.isHttps)) {
      continuation.resume(null)
      return@suspendCancellableCoroutine
    }

    val call = client.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        if (continuation.isActive) continuation.resume(null)
      }

      override fun onResponse(call: Call, response: okhttp3.Response) {
        val bytes = try {
          response.use {
            if (response.priorResponse != null || response.request.url.host != allowedHost ||
              (!allowHttpForTests && !response.request.url.isHttps) || !response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            val contentType = body.contentType()?.type?.lowercase(Locale.ROOT)
            if (contentType != null && contentType != "image") return@use null
            if (body.contentLength() > MAX_FAVICON_BYTES) return@use null

            body.byteStream().use { input ->
              val output = ByteArrayOutputStream()
              val buffer = ByteArray(8 * 1024)
              var total = 0
              while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_FAVICON_BYTES) return@use null
                output.write(buffer, 0, read)
              }
              output.toByteArray()
            }
          }
        } catch (_: Exception) {
          null
        }
        if (continuation.isActive) continuation.resume(bytes)
      }
    })
  }

  private fun decode(bytes: ByteArray): Bitmap? {
    if (bytes.size < PNG_SIGNATURE.size || !bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
      return null
    }
    try {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
        bounds.outWidth > MAX_FAVICON_DIMENSION || bounds.outHeight > MAX_FAVICON_DIMENSION) return null

      val sampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / CACHED_FAVICON_DIMENSION)
        .coerceAtLeast(1)
      val decoded = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize; inPreferredConfig = Bitmap.Config.ARGB_8888 }
      ) ?: return null
      val largestDimension = maxOf(decoded.width, decoded.height)
      if (largestDimension <= CACHED_FAVICON_DIMENSION) return decoded

      val scale = CACHED_FAVICON_DIMENSION.toFloat() / largestDimension
      val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1),
        true
      )
      if (scaled !== decoded) decoded.recycle()
      return scaled
    } catch (_: Exception) {
      return null
    }
  }
}

private val defaultNextDnsFaviconLoader = NextDnsFaviconLoader()

@Composable
fun FaviconImage(
  domain: String,
  modifier: Modifier = Modifier
) {
  val appContext = LocalContext.current.applicationContext
  val bitmap = produceState<Bitmap?>(initialValue = null, key1 = appContext, key2 = domain) {
    value = defaultNextDnsFaviconLoader.load(domain)
  }.value

  if (bitmap == null) {
    Icon(
      imageVector = Icons.Default.Language,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = modifier.testTag("remote_favicon_image")
    )
  } else {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = modifier.testTag("remote_favicon_image")
    )
  }
}


