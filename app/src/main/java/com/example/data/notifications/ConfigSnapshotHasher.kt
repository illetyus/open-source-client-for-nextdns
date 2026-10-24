package com.example.data.notifications

import com.example.data.api.AllowDenyRuleDto
import com.example.data.api.ParentalControlDto
import com.example.data.api.PrivacyDto
import com.example.data.api.SecurityDto
import com.example.data.api.SettingsDto
import java.security.MessageDigest

data class NotificationConfigSnapshot(
  val security: SecurityDto,
  val privacy: PrivacyDto,
  val parental: ParentalControlDto,
  val settings: SettingsDto,
  val denylist: List<AllowDenyRuleDto>,
  val allowlist: List<AllowDenyRuleDto>
)

object ConfigSnapshotHasher {

  fun digest(snapshot: NotificationConfigSnapshot): String {
    val canonical = buildString {
      append("security:")
      appendBool(snapshot.security.threatIntelligenceFeeds)
      appendBool(snapshot.security.aiThreatDetection)
      appendBool(snapshot.security.googleSafeBrowsing)
      appendBool(snapshot.security.cryptojacking)
      appendBool(snapshot.security.dnsRebinding)
      appendBool(snapshot.security.idnHomographs)
      appendBool(snapshot.security.typosquatting)
      appendBool(snapshot.security.dga)
      appendBool(snapshot.security.nrd)
      appendBool(snapshot.security.ddns)
      appendBool(snapshot.security.parking)
      appendBool(snapshot.security.csam)
      appendList(snapshot.security.tlds.orEmpty().map { it.id })
      append("unknown-security:")
      appendCanonicalValue(snapshot.security.unknownFields)
      append("overflow:").append(snapshot.security.unknownFieldOverflowCount).append(';')
      append(snapshot.security.unknownFieldOverflowDigest).append(';')

      append("|privacy:")
      appendBool(snapshot.privacy.disguisedTrackers)
      appendBool(snapshot.privacy.allowAffiliateLinks)
      appendList(snapshot.privacy.blocklists.orEmpty().map { it.id })
      appendList(snapshot.privacy.natives.orEmpty().map { it.id })

      append("|parental:")
      appendBool(snapshot.parental.safeSearch)
      appendBool(snapshot.parental.youtubeRestrictedMode)
      appendBool(snapshot.parental.blockBypass)
      appendList(
        snapshot.parental.services.orEmpty()
          .map { "${it.id}:${it.active.token()}" }
      )
      appendList(
        snapshot.parental.categories.orEmpty()
          .map { "${it.id}:${it.active.token()}" }
      )

      append("|settings:")
      val logs = snapshot.settings.logs
      appendBool(logs?.enabled)
      append(logs?.retention ?: "null").append(';')
      append(logs?.location ?: "null").append(';')
      appendBool(logs?.drop?.ip)
      appendBool(logs?.drop?.domain)

      val blockPage = snapshot.settings.blockPage
      appendBool(blockPage?.enabled)

      val performance = snapshot.settings.performance
      appendBool(performance?.ecs)
      appendBool(performance?.cacheBoost)
      appendBool(performance?.cnameFlattening)
      appendBool(snapshot.settings.web3)
      appendBool(snapshot.settings.bav)

      append("|deny:")
      appendList(snapshot.denylist.map { "${it.id}:${it.active.token()}" })

      append("|allow:")
      appendList(snapshot.allowlist.map { "${it.id}:${it.active.token()}" })
    }

    return MessageDigest.getInstance("SHA-256")
      .digest(canonical.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
  }

  private fun StringBuilder.appendBool(value: Boolean?) {
    append(value.token()).append(';')
  }

  private fun StringBuilder.appendList(values: List<String>) {
    values.sorted().forEach {
      append(it).append(',')
    }
    append(';')
  }

  private fun StringBuilder.appendCanonicalValue(value: Any?) {
    when (value) {
      null -> append('n')
      is Boolean -> append(if (value) "b1" else "b0")
      is String -> appendSized('s', value)
      is Number -> appendSized('d', value.toString())
      is Map<*, *> -> {
        append('{')
        value.entries
          .filter { it.key is String }
          .sortedBy { it.key as String }
          .forEach { (key, nestedValue) ->
            appendSized('k', key as String)
            appendCanonicalValue(nestedValue)
          }
        append('}')
      }
      is Iterable<*> -> {
        append('[')
        value.forEach { appendCanonicalValue(it) }
        append(']')
      }
      else -> appendSized('x', value.javaClass.name)
    }
  }

  private fun StringBuilder.appendSized(type: Char, value: String) {
    append(type).append(value.length).append(':').append(value)
  }

  private fun Boolean?.token(): String = when (this) {
    true -> "1"
    false -> "0"
    null -> "?"
  }
}
