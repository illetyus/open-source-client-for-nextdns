package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupEndpointsTest {
  @Test
  fun missingIpv4DoesNotCreateResolverAddresses() {
    assertTrue(setupIpv4Addresses(null).isEmpty())
    assertTrue(setupIpv4Addresses(emptyList()).isEmpty())
    assertTrue(profileSetupEndpoints("abc123").ipv4.isEmpty())
  }

  @Test
  fun ipv4ValuesMustBeValidLiteralsAndAreDeduplicated() {
    assertEquals(
      listOf("45.90.28.0", "45.90.30.0"),
      setupIpv4Addresses(
        listOf(
          " 45.90.28.0 ",
          "45.90.28.0",
          "45.90.30.0",
          "256.1.1.1",
          "resolver.example",
          "2001:db8::1"
        )
      )
    )
  }

  @Test
  fun profileSpecificIpv6UsesOnlyAValidSixCharacterProfileId() {
    assertEquals(
      listOf("2a07:a8c0::ab:c123", "2a07:a8c1::ab:c123"),
      profileSetupEndpoints("ABC123").ipv6
    )
    assertTrue(profileSetupEndpoints("not-a-profile").ipv6.isEmpty())
  }
}

