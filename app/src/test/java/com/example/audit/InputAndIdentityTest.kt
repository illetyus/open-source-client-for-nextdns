package com.example.audit

import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test

class InputAndIdentityTest {
  @Test fun internationalDomainsAndWildcardRulesNormalizeToAscii() {
    val cases = mapOf(
      " EXAMPLE.COM " to "example.com",
      "BÜCHER.DE." to "xn--bcher-kva.de",
      "пример.рф" to "xn--e1afmkfd.xn--p1ai",
      "xn--e1afmkfd.xn--p1ai" to "xn--e1afmkfd.xn--p1ai",
      "*.Example.COM" to "*.example.com",
      "I.EXAMPLE" to "i.example")
    cases.forEach { (input, expected) -> assertEquals(input, expected, normalizeDomainRule(input)) }
  }

  @Test fun invalidLabelsUrlsIpsAndOversizedRulesAreRejected() {
    val invalid = listOf("", "a..com", "a-.com", "-a.com", "a.com..", "*.a..com", "*.*.com",
      "https://example.com", "example.com/path", "a.com?x=1", "a@b.com", "192.168.1.1", "localhost",
      "a".repeat(64) + ".com", "xn--a.com", "a.com\u0000", "a.com\nX-Api-Key: injected")
    invalid.forEach { assertNull(it, normalizeDomainRule(it)) }
    val maximum = listOf("a".repeat(63), "b".repeat(63), "c".repeat(63), "d".repeat(61)).joinToString(".")
    assertEquals(maximum, normalizeDomainRule(maximum))
    assertNull(normalizeDomainRule(maximum + "d"))
    assertNull(normalizeDomainRule("*.$maximum"))
  }

  @Test fun missingSetupIpv4IsUnavailable() {
    assertEquals(emptyList<String>(), setupIpv4Addresses(null))
    assertEquals(emptyList<String>(), setupIpv4Addresses(emptyList()))
    assertEquals(emptyList<String>(), setupIpv4Addresses(listOf("not-an-ip", "256.1.1.1", "192.168.1")))
  }

  @Test fun profileIpv6EndpointsComeFromTheValidatedProfileId() {
    assertEquals(listOf("2a07:a8c0::ab:cdef", "2a07:a8c1::ab:cdef"), profileIpv6Addresses("abcdef"))
    assertEquals(emptyList<String>(), profileIpv6Addresses("not-hex"))
    assertEquals(emptyList<String>(), profileIpv6Addresses("abc"))
  }

  @Test fun diagnosticIdentityUsesFingerprintWhileErrorsAndMissingIdentityRemainUnverified() {
    val profile = NextDnsProfile("aaaaaa", "A", "fp0123456789abcdef")
    val result = DiagnosticTestResult(status = "ok", profileId = "fp0123456789abcdef", lastTestedTime = 1_000)
    assertEquals(DiagnosticConnectionState.MATCHED_PROFILE, diagnosticConnectionState(profile, result))
    assertEquals(DiagnosticConnectionState.UNVERIFIED_PROFILE, diagnosticConnectionState(profile, result.copy(profileId = "aaaaaa")))
    assertEquals(DiagnosticConnectionState.UNVERIFIED_PROFILE, diagnosticConnectionState(profile.copy(fingerprint = ""), result))
    assertEquals(DiagnosticConnectionState.UNVERIFIED_PROFILE, diagnosticConnectionState(profile, result.copy(profileId = "")))
    assertEquals(DiagnosticConnectionState.ERROR, diagnosticConnectionState(profile, result.copy(errorMessage = "timeout")))
    assertEquals(DiagnosticConnectionState.UNCONFIGURED, diagnosticConnectionState(profile, result.copy(lastTestedTime = 0)))
    assertEquals(DiagnosticConnectionState.NO_SELECTED_PROFILE, diagnosticConnectionState(null, result))
    assertEquals(DiagnosticConnectionState.TESTING, diagnosticConnectionState(profile, result, running = true))
  }
}
