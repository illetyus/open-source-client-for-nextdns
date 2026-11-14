package com.example.performance

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class BenchmarkIconModeTest {
  @Test fun defaultFixtureKeepsTheExistingUniqueUrlsAndScaleOrder() {
    val mode = BenchmarkIconMode.fromArgument(null)
    val key = "736974652d312e696e76616c6964"
    assertEquals(listOf("favicons/$key@3x.png", "favicons/$key@2x.png", "favicons/$key@1x.png"),
      mode.pathsFor("site-1.invalid"))
    assertNotEquals(mode.pathsFor("site-1.invalid"), mode.pathsFor("site-2.invalid"))
    assertFalse(mode.preload)
  }

  @Test fun repeatedModeSharesOneIconWithoutPretendingItWasPreloaded() {
    val mode = BenchmarkIconMode.fromArgument("repeated")
    assertFalse(mode.preload)
    assertEquals(mode.pathsFor("site-1.invalid"), mode.pathsFor("site-2.invalid"))
  }

  @Test fun warmRepeatedModeRequiresAnExplicitPreload() {
    val mode = BenchmarkIconMode.fromArgument("warm-repeated")
    assertTrue(mode.preload)
    assertEquals(BenchmarkIconMode.fromArgument("repeated").pathsFor("site-2.invalid"),
      mode.pathsFor("site-1.invalid"))
  }

  @Test fun uniqueModePreservesTheLegacyRawInputEncoding() {
    val mode = BenchmarkIconMode.fromArgument("unique")
    assertFalse(mode.preload)
    assertNotEquals(mode.pathsFor("EXAMPLE.COM."), mode.pathsFor("example.com"))
    assertEquals("favicons/49@3x.png", mode.pathsFor("I").first())
  }

  @Test fun fixtureUrlKeysAreLocaleIndependentAndCannotCreatePathSegments() {
    val mode = BenchmarkIconMode.fromArgument("unique")
    val english = mode.pathsFor("I.EXAMPLE.COM")
    val previous = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("tr"))
      assertEquals(english, mode.pathsFor("I.EXAMPLE.COM"))
      mode.pathsFor("../../x?y=z").forEach {
        assertTrue(Regex("favicons/[0-9a-f]+@[123]x\\.png").matches(it))
      }
    } finally {
      Locale.setDefault(previous)
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun unknownModeIsRejectedInsteadOfSilentlyChangingTheWorkload() {
    BenchmarkIconMode.fromArgument("typo")
  }
}
