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
import okhttp3.mockwebserver.SocketPolicy
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
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

  @Test fun overlappingConsumersShareOneRequestAndBitmap() = runBlocking {
    server.enqueue(imageResponse(PNG_BYTES).setBodyDelay(200, TimeUnit.MILLISECONDS))
    val loader = loader()
    val first = async(start = CoroutineStart.UNDISPATCHED) { loader.load("example.com") }
    val second = async(start = CoroutineStart.UNDISPATCHED) { loader.load("EXAMPLE.COM.") }

    val a = first.await()
    val b = second.await()
    assertNotNull(a)
    assertTrue("Consumers should share the decoded bitmap", a === b)
    assertEquals("Only one network request for overlapping consumers", 1, server.requestCount)
  }

  @Test fun cancellingOneConsumerDoesNotCancelAnother() = runBlocking {
    server.enqueue(imageResponse(PNG_BYTES).setBodyDelay(200, TimeUnit.MILLISECONDS))
    val loader = loader()
    val first = async(start = CoroutineStart.UNDISPATCHED) { loader.load("example.com") }
    val second = async(start = CoroutineStart.UNDISPATCHED) { loader.load("example.com") }
    assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    first.cancelAndJoin()

    assertNotNull("The remaining consumer still receives its image", second.await())
    assertEquals(1, server.requestCount)
  }

  @Test fun failedSharedLoadCanBeRetried() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(503))
    val loader = loader()
    assertNull(loader.load("example.com"))
    server.enqueue(imageResponse(PNG_BYTES))
    assertNotNull(loader.load("example.com"))
    assertEquals("Failures must not be negatively cached", 2, server.requestCount)
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

  @Test fun malformedHighResolutionImageFallsBackToTheNextSupportedSize() = runBlocking {
    server.enqueue(imageResponse("not a bitmap".toByteArray()))
    server.enqueue(imageResponse(PNG_BYTES))
    val loader = NextDnsFaviconLoader(
      client = NextDnsFaviconHttp.client,
      urlsForDomain = { domain ->
        listOf("3x", "2x", "1x").map { scale -> server.url("/$domain@$scale.png").toString() }
      },
      allowedHost = server.hostName,
      allowHttpForTests = true
    )

    val bitmap = loader.load("example.com")

    assertNotNull("A malformed @3x image should fall back to a valid @2x image", bitmap)
    assertEquals(2, server.requestCount)
    assertEquals("/example.com@3x.png", server.takeRequest().path)
    assertEquals("/example.com@2x.png", server.takeRequest().path)
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
      urlsForDomain = { listOf("https://unexpected.invalid/favicon") },
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
        // Hold the socket until cancellation, rather than racing a five-second
        // headers timer against MockWebServer's own five-second shutdown limit.
        .setSocketPolicy(SocketPolicy.NO_RESPONSE)
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
      urlsForDomain = { listOf(server.url("/favicon").toString()) },
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
    val scanline = ByteArray(1 + side * 4)
    for (pixel in 0 until side) {
      val offset = 1 + pixel * 4
      scanline[offset] = 0x33
      scanline[offset + 1] = 0x66
      scanline[offset + 2] = 0x99.toByte()
      scanline[offset + 3] = 0xff.toByte()
    }
    val rawImage = ByteArrayOutputStream().use { output ->
      repeat(side) { output.write(scanline) }
      output.toByteArray()
    }
    val compressedImage = ByteArrayOutputStream().use { output ->
      java.util.zip.DeflaterOutputStream(output).use { it.write(rawImage) }
      output.toByteArray()
    }
    val png = ByteArrayOutputStream()
    png.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))

    fun writeChunk(type: String, data: ByteArray) {
      val typeBytes = type.toByteArray(Charsets.US_ASCII)
      val crc = java.util.zip.CRC32().apply { update(typeBytes); update(data) }
      png.write(byteArrayOf(
        (data.size ushr 24).toByte(),
        (data.size ushr 16).toByte(),
        (data.size ushr 8).toByte(),
        data.size.toByte()
      ))
      png.write(typeBytes)
      png.write(data)
      png.write(byteArrayOf(
        (crc.value ushr 24).toByte(),
        (crc.value ushr 16).toByte(),
        (crc.value ushr 8).toByte(),
        crc.value.toByte()
      ))
    }

    val header = ByteArrayOutputStream().also { output ->
      java.io.DataOutputStream(output).use { data ->
        data.writeInt(side)
        data.writeInt(side)
        data.writeByte(8)
        data.writeByte(6)
        data.writeByte(0)
        data.writeByte(0)
        data.writeByte(0)
      }
    }.toByteArray()
    writeChunk("IHDR", header)
    writeChunk("IDAT", compressedImage)
    writeChunk("IEND", byteArrayOf())
    return png.toByteArray()
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

