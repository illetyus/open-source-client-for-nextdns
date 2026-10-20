package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.IDN
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

private const val FAVICON_HOST = "favicons.nextdns.io"
private const val MAX_FAVICON_BYTES = 128 * 1024
private const val MAX_FAVICON_DIMENSION = 1024
private const val CACHED_FAVICON_DIMENSION = 96
private const val MAX_CACHED_FAVICONS = 96
internal const val FAVICON_TOTAL_TIMEOUT_MS = 8_000
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

private object NextDnsFaviconLoader {
  private val cache = object : LinkedHashMap<String, Bitmap>(MAX_CACHED_FAVICONS, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
      size > MAX_CACHED_FAVICONS
  }

  suspend fun load(domain: String): Bitmap? {
    val url = nextDnsFaviconUrl(domain) ?: return null
    synchronized(cache) { cache[url] }?.let { return it }
    val downloaded = withContext(Dispatchers.IO) { download(url) } ?: return null
    synchronized(cache) {
      cache[url]?.let { return it }
      cache[url] = downloaded
    }
    return downloaded
  }

  private fun download(url: String): Bitmap? {
    val request = Request.Builder()
      .url(url)
      .header("Accept", "image/png,image/*;q=0.8")
      .get()
      .build()
    try {
      NextDnsFaviconHttp.client.newCall(request).execute().use { response ->
        if (response.request.url.host != FAVICON_HOST || !response.isSuccessful) return null
        val body = response.body ?: return null
        val contentType = body.contentType()?.type?.lowercase(Locale.ROOT)
        if (contentType != null && contentType != "image") return null
        if (body.contentLength() > MAX_FAVICON_BYTES) return null

        val bytes = body.byteStream().use { input ->
          val output = ByteArrayOutputStream()
          val buffer = ByteArray(8 * 1024)
          var total = 0
          while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_FAVICON_BYTES) return null
            output.write(buffer, 0, read)
          }
          output.toByteArray()
        }
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
      }
    } catch (_: Exception) {
      return null
    }
  }
}

@Composable
fun FaviconImage(
  domain: String,
  modifier: Modifier = Modifier
) {
  val appContext = LocalContext.current.applicationContext
  val bitmap = produceState<Bitmap?>(initialValue = null, key1 = appContext, key2 = domain) {
    value = NextDnsFaviconLoader.load(domain)
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
