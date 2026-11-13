package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NextDnsFaviconUrlTest {
  @Test fun encodesNormalizedAsciiHostUnderTheFixedNextDnsOrigin() {
    assertEquals(
      "https://favicons.nextdns.io/hex:6578616d706c652e636f6d@3x.png",
      nextDnsFaviconUrl("EXAMPLE.COM.")
    )
  }

  @Test fun convertsInternationalHostnamesToAsciiBeforeHexEncoding() {
    assertEquals(
      "https://favicons.nextdns.io/hex:786e2d2d62636865722d6b76612e6465@3x.png",
      nextDnsFaviconUrl("bücher.de")
    )
  }

  @Test fun requestsLargestObservedSizeFirstAndFallsBackToSmallerSizes() {
    val encoded = "6578616d706c652e636f6d"
    assertEquals(
      listOf(
        "https://favicons.nextdns.io/hex:$encoded@3x.png",
        "https://favicons.nextdns.io/hex:$encoded@2x.png",
        "https://favicons.nextdns.io/hex:$encoded@1x.png"
      ),
      nextDnsFaviconUrls("example.com")
    )
  }

  @Test fun rejectsValuesThatAreNotDnsHostnames() {
    listOf(
      "", "   ", "https://example.com", "example.com/path", "*.example.com",
      "user@example.com", "example.com:443", "example..com", "_dns.example.com",
      "127.0.0.1", "example.com?x=1", "-bad.example", "bad-.example",
      "router.local", "printer.internal", "host.home.arpa", "127.0.0.1.nip.io"
    ).forEach { input -> assertNull("Rejected: $input", nextDnsFaviconUrl(input)) }
  }

  @Test fun faviconHttpClientBoundsTheFullCallAndDoesNotFollowRedirects() {
    val client = NextDnsFaviconHttp.client

    assertEquals(FAVICON_TOTAL_TIMEOUT_MS, client.callTimeoutMillis)
    assertEquals(3_000, client.connectTimeoutMillis)
    assertEquals(3_000, client.readTimeoutMillis)
    assertFalse(client.followRedirects)
    assertFalse(client.followSslRedirects)
  }
}
