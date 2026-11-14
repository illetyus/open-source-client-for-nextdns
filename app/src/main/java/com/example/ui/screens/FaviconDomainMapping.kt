package com.example.ui.screens

import java.net.URI
import java.util.Locale

private val nativeTrackingDomains = mapOf(
  "windows" to "microsoft.com",
  "apple" to "apple.com",
  "samsung" to "samsung.com",
  "xiaomi" to "mi.com",
  "huawei" to "huawei.com",
  "roku" to "roku.com",
  "sonos" to "sonos.com",
  "alexa" to "alexa.com",
  "amazon-alexa" to "alexa.com",
  "lg" to "lg.com"
)

private val parentalServiceDomains = mapOf(
  "tiktok" to "tiktok.com",
  "instagram" to "instagram.com",
  "facebook" to "facebook.com",
  "twitter" to "twitter.com",
  "x" to "x.com",
  "youtube" to "youtube.com",
  "reddit" to "reddit.com",
  "roblox" to "roblox.com",
  "discord" to "discord.com",
  "steam" to "steampowered.com"
)

internal fun nativeTrackingFaviconDomain(id: String): String? {
  val normalized = id.trim().lowercase(Locale.ROOT)
    .removePrefix("native:")
    .removePrefix("native-")
    .replace(' ', '-')
  return nativeTrackingDomains[normalized]
}

internal fun parentalServiceFaviconDomain(id: String, website: String?): String? {
  website?.let(::websiteHostname)?.let { host ->
    if (nextDnsFaviconUrl(host) != null) return host
  }

  val normalized = id.trim().lowercase(Locale.ROOT)
    .removePrefix("native:")
    .removePrefix("native-")
    .replace(' ', '-')
  return parentalServiceDomains[normalized]
}

private fun websiteHostname(website: String): String? {
  val value = website.trim()
  if (value.isEmpty()) return null
  val absoluteUrl = if (value.contains("://")) value else "https://$value"
  val uri = runCatching { URI(absoluteUrl) }.getOrNull() ?: return null
  if (!uri.scheme.equals("https", ignoreCase = true) && !uri.scheme.equals("http", ignoreCase = true)) {
    return null
  }
  if (uri.rawUserInfo != null) return null
  return uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
}
