package com.example.ui.screens

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Buffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NextDnsFaviconNetworkTest {
  private lateinit var server: MockWebServer

  @Before fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After fun tearDown() {
    server.shutdown()
  }

  @Test fun validImageLoadsAndIsCachedInMemory() = runBlocking {
    server.enqueue(imageResponse(PNG_BYTES))
    val loader = loader()

    val first = loader.load("example.com")
    val second = loader.load("EXAMPLE.COM.")

    assertNotNull(first)
    assertNotNull(second)
    assertEquals(1, server.requestCount)
  }

  @Test fun highResolutionSourceIsRetainedForSharperDomainIcons() = runBlocking {
    val source = png(384)
    assertTrue("High-resolution fixture must fit within the download limit", source.size <= 128 * 1024)
    server.enqueue(imageResponse(source))

    val bitmap = loader().load("example.com")

    assertNotNull("A valid 384px PNG within the byte limit should decode", bitmap)
    assertEquals("Decoded width should be capped at 192px", 192, bitmap!!.width)
    assertEquals("Decoded height should be capped at 192px", 192, bitmap.height)
  }

  @Test fun redirectIsRejectedWithoutRequestingItsTarget() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/redirect-target")))

    assertNull(loader().load("example.com"))
    assertEquals(1, server.requestCount)
    assertEquals("/favicon", server.takeRequest().path)
  }

  @Test fun nonImageMimeTypeIsRejected() = runBlocking {
    server.enqueue(MockResponse().setBody(Buffer().write(PNG_BYTES)).addHeader("Content-Type", "text/html"))

    assertNull(loader().load("example.com"))
  }

  @Test fun oversizedChunkedBodyIsRejectedEvenWithoutContentLength() = runBlocking {
    val oversized = oversizedPng()
    assertTrue("Fixture must exceed the loader's 128 KiB limit", oversized.size > 128 * 1024)
    server.enqueue(MockResponse().setChunkedBody(Buffer().write(oversized), 8 * 1024))

    assertNull(loader().load("example.com"))
  }

  @Test fun cleartextUrlIsRejectedBeforeAnyRequest() = runBlocking {
    val client = loader(allowHttpForTests = false)

    assertNull(client.load("example.com"))
    assertEquals(0, server.requestCount)
  }

  @Test fun unexpectedHostIsRejectedBeforeAnyRequest() = runBlocking {
    val client = NextDnsFaviconLoader(
      client = NextDnsFaviconHttp.client,
      urlForDomain = { "https://unexpected.invalid/favicon" },
      allowedHost = server.hostName
    )

    assertNull(client.load("example.com"))
    assertEquals(0, server.requestCount)
  }

  @Test fun malformedImageBytesAreRejected() = runBlocking {
    server.enqueue(imageResponse("not a bitmap".toByteArray()))

    assertNull(loader().load("example.com"))
  }

  @Test fun wholeCallTimeoutReturnsFallbackResult() = runBlocking {
    server.enqueue(
      MockResponse().setHeadersDelay(2, TimeUnit.SECONDS)
        .setBody(Buffer().write(PNG_BYTES)).addHeader("Content-Type", "image/png")
    )
    val client = OkHttpClient.Builder()
      .callTimeout(100, TimeUnit.MILLISECONDS)
      .build()

    assertNull(loader(client).load("example.com"))
  }

  @Test fun cancellingLoadCancelsTheUnderlyingOkHttpCall() = runBlocking {
    val callCancelled = CountDownLatch(1)
    server.enqueue(
      MockResponse()
        .setHeadersDelay(5, TimeUnit.SECONDS)
        .setBody(Buffer().write(PNG_BYTES))
        .addHeader("Content-Type", "image/png")
    )
    val client = OkHttpClient.Builder()
      .eventListenerFactory {
        object : EventListener() {
          override fun callFailed(call: Call, ioe: IOException) {
            if (call.isCanceled()) callCancelled.countDown()
          }
        }
      }
      .build()
    val job = launch(Dispatchers.IO) { loader(client).load("example.com") }

    assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    job.cancelAndJoin()

    assertTrue("Coroutine cancellation should cancel the underlying HTTP call", callCancelled.await(2, TimeUnit.SECONDS))
    assertFalse(job.isActive)
  }

  private fun loader(
    client: OkHttpClient = NextDnsFaviconHttp.client,
    allowHttpForTests: Boolean = true
  ): NextDnsFaviconLoader =
    NextDnsFaviconLoader(
      client = client,
      urlForDomain = { server.url("/favicon").toString() },
      allowedHost = server.hostName,
      allowHttpForTests = allowHttpForTests
    )

  private fun oversizedPng(): ByteArray {
    val side = 512
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(side * side) { index ->
      val noise = index * 1_103_515_245 + 12_345
      (0xff shl 24) or ((noise ushr 16 and 0xff) shl 16) or
        ((noise ushr 8 and 0xff) shl 8) or (noise and 0xff)
    }
    bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
    return ByteArrayOutputStream().use { output ->
      check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
      bitmap.recycle()
      output.toByteArray()
    }
  }

  private fun png(side: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(side * side) { index ->
      val shade = index % 256
      (0xff shl 24) or (shade shl 16) or ((255 - shade) shl 8) or shade
    }
    bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
    return ByteArrayOutputStream().use { output ->
      check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
      bitmap.recycle()
      output.toByteArray()
    }
  }

  private fun imageResponse(bytes: ByteArray): MockResponse =
    MockResponse().setBody(Buffer().write(bytes)).addHeader("Content-Type", "image/png")

  private companion object {
    val PNG_BYTES: ByteArray = Base64.getDecoder().decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/" +
        "8ioAAAAASUVORK5CYII="
    )
  }
}

