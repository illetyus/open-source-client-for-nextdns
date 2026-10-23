package com.example.data.model

import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

/** Setup values derived from documented profile identifiers or supplied by a verified source. */
data class ProfileSetupEndpoints(
  val ipv4: List<String> = emptyList(),
  val ipv6: List<String> = emptyList()
)

/** Only resolver/setup sinks require the documented six-hex identifier form. */
fun normalizedSetupProfileId(profileId: String?): String? = profileId
  ?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.matches(Regex("[0-9a-f]{6}")) }

fun macOsNextDnsInstallCommand(profileId: String?): String? = normalizedSetupProfileId(profileId)?.let { id ->
  "brew install nextdns/tap/nextdns && sudo nextdns install -config $id -auto-activate"
}

/** NextDNS profile-specific IPv6 resolver addresses use the six-hex-character profile ID. */
fun profileIpv6Addresses(profileId: String?): List<String> {
  val id = normalizedSetupProfileId(profileId) ?: return emptyList()
  val suffix = "${id.take(2)}:${id.drop(2)}"
  return listOf("2a07:a8c0::$suffix", "2a07:a8c1::$suffix")
}

/** Profile-specific IPv4 resolver addresses depend on linked-IP state and are not in the public API. */
fun setupIpv4Addresses(addresses: List<String>?): List<String> = addresses.orEmpty()
  .map(String::trim)
  .filter { address ->
    val octets = address.split('.')
    octets.size == 4 && octets.all { part ->
      part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull()?.let { it in 0..255 } == true
    }
  }
  .distinct()

/** Accept only literal IPv6 addresses; never resolve a hostname. */
fun setupIpv6Addresses(setup: ProfileSetupEndpoints?): List<String> = setup?.ipv6.orEmpty()
  .map(String::trim)
  .filter { value ->
    value.contains(':') && value.matches(Regex("[0-9a-fA-F:]+")) &&
      runCatching { InetAddress.getByName(value) is Inet6Address }.getOrDefault(false)
  }.distinct()

fun profileSetupEndpoints(profileId: String?): ProfileSetupEndpoints = ProfileSetupEndpoints(
  ipv4 = emptyList(),
  ipv6 = profileIpv6Addresses(profileId)
)
