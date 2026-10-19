package com.example.data.model

import java.net.IDN
import java.util.Locale

/** Canonical API rule: optional leading wildcard and an IDNA ASCII hostname, never a URL/IP. */
fun normalizeDomainRule(input: String): String? {
  val trimmed = input.trim()
  if (trimmed.isEmpty() || trimmed.length > 1024) return null
  val wildcard = trimmed.startsWith("*.")
  val hostname = if (wildcard) trimmed.substring(2) else trimmed
  val ascii = try {
    IDN.toASCII(hostname, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT).removeSuffix(".")
  } catch (_: IllegalArgumentException) { return null }
  val labels = ascii.split('.')
  val canonical = if (wildcard) "*.$ascii" else ascii
  if (canonical.length > 253 || labels.size < 2 || labels.last().all(Char::isDigit)) return null
  if (labels.any { label ->
    label.isEmpty() || label.length > 63 || !label.matches(Regex("[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?")) ||
      (label.startsWith("xn--") && IDN.toUnicode(label, IDN.USE_STD3_ASCII_RULES) == label)
  }) return null
  return canonical
}
