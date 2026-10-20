package com.example.ui.screens

import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.toMediaType
import okhttp3.toResponseBody
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Buffer

@RunWith(RobolectricTestRunner::class)
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

  @Test fun redirectIsRejectedWithoutRequestingItsTarget() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/redirect-target")))

    assertNull(loader().load("example.com"))
    assertEquals(1, server.requestCount)
    assertEquals("/favicon", server.takeRequest().path)
  }

  @Test fun nonImageMimeTypeIsRejected() = runBlocking {
    server.enqueue(MockResponse().setBody("<html>not an icon</html>").addHeader("Content-Type", "text/html"))

    assertNull(loader().load("example.com"))
  }

  @Test fun oversizedChunkedBodyIsRejectedEvenWithoutContentLength() = runBlocking {
    val oversized = ByteArray(128 * 1024 + 1) { 0x41 }
    server.enqueue(MockResponse().setChunkedBody(Buffer().write(oversized), 8 * 1024))

    assertNull(loader().load("example.com"))
  }

  @Test fun malformedImageBytesAreRejected() = runBlocking {
    server.enqueue(imageResponse("not a bitmap".toByteArray()))

    assertNull(loader().load("example.com"))
  }

  @Test fun wholeCallTimeoutReturnsFallbackResult() = runBlocking {
    server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setBody(PNG_BYTES.toResponseBody("image/png".toMediaType())))
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
        .setBody(PNG_BYTES.toResponseBody("image/png".toMediaType()))
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

  private fun loader(client: OkHttpClient = NextDnsFaviconHttp.client): NextDnsFaviconLoader =
    NextDnsFaviconLoader(
      client = client,
      urlForDomain = { server.url("/favicon").toString() },
      allowedHost = server.hostName
    )

  private fun imageResponse(bytes: ByteArray): MockResponse =
    MockResponse().setBody(bytes.toResponseBody("image/png".toMediaType()))

  private companion object {
    val PNG_BYTES: ByteArray = Base64.getDecoder().decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/" +
        "8ioAAAAASUVORK5CYII="
    )
  }
}

