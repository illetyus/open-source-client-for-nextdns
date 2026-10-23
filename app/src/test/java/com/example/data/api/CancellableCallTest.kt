package com.example.data.api

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CancellableCallTest {
  @Test fun unsafeDiagnosticProfileDoesNotCreateAnyRequest() = runBlocking {
    for (profileId in listOf("ignored@attacker.example/", "abc123; printf P08_MARKER; #", "benchmark-profile")) {
      var requested = false
      val result = NextDnsNetworkClient.fetchTestConnectionDirect(profileId, Call.Factory {
        requested = true
        error("Unsafe profile must not create a request")
      })
      assertNull(result)
      assertFalse(profileId, requested)
    }
  }

  @Test fun normalizedDiagnosticProfileCannotChangeAuthority() = runBlocking {
    val body = TrackingBody("{\"status\":\"ok\",\"profile\":\"abc123\"}")
    var captured: Request? = null
    val result = NextDnsNetworkClient.fetchTestConnectionDirect(" ABC123 ", Call.Factory {
      captured = it
      FixtureCall(it, body)
    })
    assertEquals("ok", result?.status)
    assertTrue(captured!!.url.host.matches(Regex("[0-9a-f]{12}-abc123\\.test\\.nextdns\\.io")))
    assertEquals("https", captured!!.url.scheme)
    assertNull(captured!!.header("X-Api-Key"))
    assertTrue(body.closed.get())
  }

  private class TrackingBody(text: String, private val blockRead: Boolean = false) : ResponseBody() {
    val closed = AtomicBoolean()
    val readStarted = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val aborted = AtomicBoolean()
    private val buffered = object : ForwardingSource(Buffer().writeUtf8(text)) {
      override fun read(sink: Buffer, byteCount: Long): Long {
        if (blockRead) {
          readStarted.countDown()
          check(release.await(5, TimeUnit.SECONDS)) { "Blocked fixture was not cancelled" }
          if (aborted.get()) throw IOException("Cancelled body read")
        }
        return super.read(sink, byteCount)
      }
      override fun close() { closed.set(true); super.close() }
    }.buffer()
    fun abort() { aborted.set(true); release.countDown() }
    override fun contentType(): MediaType? = null
    override fun contentLength() = -1L
    override fun source(): BufferedSource = buffered
  }

  private class FixtureCall(
    private val request: Request,
    val body: TrackingBody,
    private val code: Int = 200,
    private val immediate: Boolean = true
  ) : Call {
    val entered = CountDownLatch(1)
    private val cancelled = AtomicBoolean()
    private lateinit var callback: Callback
    override fun request() = request
    override fun execute(): Response = error("The cancellable bridge must enqueue")
    override fun enqueue(responseCallback: Callback) {
      callback = responseCallback
      entered.countDown()
      if (immediate) respond()
    }
    fun respond() { callback.onResponse(this, Response.Builder().request(request).code(code)
      .message("Fixture").protocol(Protocol.HTTP_1_1).body(body).build()) }
    override fun cancel() { cancelled.set(true); body.abort() }
    override fun isExecuted() = ::callback.isInitialized
    override fun isCanceled() = cancelled.get()
    override fun timeout() = Timeout()
    override fun clone(): Call = FixtureCall(request, body, code, immediate)
  }

  private fun call(body: TrackingBody, immediate: Boolean = true) =
    FixtureCall(Request.Builder().url("https://fixture.invalid/").build(), body, immediate = immediate)

  @Test fun cancellationBeforeHeadersClosesLateDeliveredResponse() = runBlocking {
    val body = TrackingBody("late")
    val call = call(body, immediate = false)
    val pending = async(Dispatchers.IO) { call.withCancellableResponse { it.body!!.string() } }
    assertTrue(call.entered.await(5, TimeUnit.SECONDS))
    withTimeout(3_000) { pending.cancelAndJoin() }
    call.respond()
    assertTrue(call.isCanceled())
    assertTrue(body.closed.get())
  }

  @Test fun cancellationDuringBlockingBodyReadCancelsCallAndClosesBody() = runBlocking {
    val body = TrackingBody("stream", blockRead = true)
    val call = call(body)
    val pending = async(Dispatchers.IO) { call.withCancellableResponse { it.body!!.string() } }
    assertTrue(body.readStarted.await(5, TimeUnit.SECONDS))
    withTimeout(3_000) { pending.cancelAndJoin() }
    assertTrue(call.isCanceled())
    assertTrue(body.closed.get())
    assertTrue(pending.isCancelled)
  }

  @Test fun exceptionInsideConsumerStillClosesResponse() = runBlocking {
    val body = TrackingBody("payload")
    try {
      call(body).withCancellableResponse<Unit> { throw IOException("Consumer failure") }
      fail("Consumer failure must propagate")
    } catch (_: IOException) { }
    assertTrue(body.closed.get())
  }

  @Test fun allCatalogHttpFailurePathsCloseTheirUnconsumedBodies() = runBlocking {
    val readers: List<suspend (Call.Factory) -> Any?> = listOf(
      { NextDnsNetworkClient.fetchAvailableBlocklistsDirect(it) },
      { NextDnsNetworkClient.fetchAvailableNativesDirect(it) },
      { NextDnsNetworkClient.fetchAvailableParentalServicesDirect(it) },
      { NextDnsNetworkClient.fetchAvailableParentalCategoriesDirect(it) },
      { NextDnsNetworkClient.fetchAvailableTldsDirect(it) }
    )
    for (reader in readers) {
      val body = TrackingBody("unread error")
      assertNull(reader(Call.Factory { FixtureCall(it, body, code = 503) }))
      assertTrue("HTTP error response must close", body.closed.get())
    }
  }

  @Test fun malformedDiagnosticJsonAlsoClosesBody() = runBlocking {
    val body = TrackingBody("not-json")
    assertNull(NextDnsNetworkClient.fetchTestConnectionDirect("aaaaaa", Call.Factory { FixtureCall(it, body) }))
    assertTrue(body.closed.get())
  }

  @Test fun diagnosticHttpFailureIgnoresSuccessLookingJson() = runBlocking {
    val body = TrackingBody("""{"status":"ok","profile":"aaaaaa"}""")
    assertNull(NextDnsNetworkClient.fetchTestConnectionDirect("aaaaaa",
      Call.Factory { FixtureCall(it, body, code = 503) }))
    assertTrue(body.closed.get())
  }
}
