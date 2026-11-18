package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconDomainMappingTest {
  @Test fun nativeTrackingBrandsUseTheirOwnDomainsAndUnknownIdsDoNotBorrowNextDnsBranding() {
    assertEquals("alexa.com", nativeTrackingFaviconDomain("native:alexa"))
    assertEquals("roku.com", nativeTrackingFaviconDomain("roku"))
    assertEquals("sonos.com", nativeTrackingFaviconDomain("SONOS"))
    assertNull(nativeTrackingFaviconDomain("future-unknown-brand"))
  }

  @Test fun parentalServicesPreferTheCatalogWebsiteAndUseKnownAliasesWhenMissing() {
    assertEquals("instagram.com", parentalServiceFaviconDomain("instagram", "https://www.instagram.com/"))
    assertEquals("steampowered.com", parentalServiceFaviconDomain("steam", null))
    assertNull(parentalServiceFaviconDomain("unknown-service", null))
  }

  @Test fun fallbackMonogramIsAVisibleSingleUnicodeCharacter() {
    assertEquals("A", faviconFallbackMonogram(" Amazon Alexa "))
    assertEquals("?", faviconFallbackMonogram("  "))
  }
}
