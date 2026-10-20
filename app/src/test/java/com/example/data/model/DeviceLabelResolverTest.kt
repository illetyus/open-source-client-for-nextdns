package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLabelResolverTest {
  private val fallback = "Unidentified device"

  @Test fun prefersServerNameThenModel() {
    assertEquals("Anna's phone", DeviceLabelResolver.resolve("Anna's phone", "Pixel 9", "A1", fallback))
    assertEquals("Pixel 9", DeviceLabelResolver.resolve(" ", "Pixel 9", "A1", fallback))
  }

  @Test fun rejectsPlaceholderAndRawIdentifierValues() {
    assertEquals("Pixel 9", DeviceLabelResolver.resolve("undefined", "Pixel 9", "A1", fallback))
    assertEquals(fallback, DeviceLabelResolver.resolve("A1", null, "A1", fallback))
    assertEquals(fallback, DeviceLabelResolver.resolve("null", null, "A1", fallback))
    assertEquals(fallback, DeviceLabelResolver.resolve(null, "__UNIDENTIFIED__", "__UNIDENTIFIED__", fallback))
  }

  @Test fun preservesAValidNameEvenWhenModelIsMissing() {
    assertEquals("Office tablet", DeviceLabelResolver.resolve("Office tablet", null, "A1", fallback))
  }
}
