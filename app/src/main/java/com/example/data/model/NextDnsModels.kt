package com.example.data.model

import java.util.UUID

const val NEXTDNS_UNIDENTIFIED_DEVICE_ID = "__UNIDENTIFIED__"

enum class DnsStatus {
  ALLOWED,
  BLOCKED
}

data class NextDnsProfile(
  val id: String = java.util.UUID.randomUUID().toString(),
  val name: String,
  val fingerprint: String = ""
)

data class SecuritySettings(
  val threatIntelligenceFeeds: Boolean = true,
  val aiThreatDetection: Boolean = true,
  val googleSafeBrowsing: Boolean = true,
  val cryptojacking: Boolean = true,
  val dnsRebinding: Boolean = true,
  val idnHomographs: Boolean = true,
  val typosquatting: Boolean = true,
  val dga: Boolean = true,
  val nrd: Boolean = true,
  val ddns: Boolean = false,
  val parkedDomains: Boolean = true,
  val csam: Boolean = true,
  val blockedTlds: List<String> = emptyList()
)

data class BlocklistEntry(
  val id: String = java.util.UUID.randomUUID().toString(),
  val name: String,
  val description: String,
  val entriesCount: Long = 0,
  val active: Boolean = false,
  val website: String? = null,
  val category: String = "Genel",
  val updatedTime: String = ""
)

data class NativeTrackingEntry(
  val id: String = java.util.UUID.randomUUID().toString(),
  val name: String,
  val description: String = "",
  val active: Boolean = false
)

data class PrivacySettings(
  val blocklists: List<BlocklistEntry> = emptyList(),
  val nativeTracking: List<NativeTrackingEntry> = emptyList(),
  val disguisedTrackers: Boolean = true,
  val allowAffiliates: Boolean = false
)

data class BlockedServiceEntry(
  val id: String = java.util.UUID.randomUUID().toString(),
  val name: String = id,
  val category: String = "Genel",
  val website: String? = null,
  val icon: String = "🔥",
  val active: Boolean = false,
  val scheduleActive: Boolean = false,
  val startHour: Int = 22,
  val endHour: Int = 7
)

data class BlockedCategoryEntry(
  val id: String = java.util.UUID.randomUUID().toString(),
  val name: String = id,
  val description: String = "",
  val active: Boolean = false
)

data class ParentalControlSettings(
  val services: List<BlockedServiceEntry> = emptyList(),
  val categories: List<BlockedCategoryEntry> = emptyList(),
  val safeSearch: Boolean = true,
  val youtubeRestrictedMode: Boolean = false,
  val blockBypass: Boolean = true
)

data class TldCatalogEntry(
  val id: String,
  val spamhaus: Int = 0
)

data class SetupEndpointInfo(
  val ipv4: List<String> = emptyList(),
  val ipv6: List<String> = emptyList(),
  val linkedIp: String? = null,
  val linkedServers: List<String> = emptyList(),
  val ddns: String? = null,
  val updateToken: String? = null,
  val dnscrypt: String? = null
)

data class AllowDenyItem(
  val id: String = java.util.UUID.randomUUID().toString(),
  val domain: String = id,
  val active: Boolean = true,
  val comment: String = "",
  val addedAt: Long = System.currentTimeMillis()
)

data class DnsLogEntry(
  val id: String = java.util.UUID.randomUUID().toString(),
  val timestamp: String = "",
  val domain: String,
  val rootDomain: String = domain,
  val deviceId: String? = null,
  val tracker: String? = null,
  val encrypted: Boolean? = null,
  val client: String? = null,
  val clientIp: String? = null,
  val deviceName: String? = null,
  val blocked: Boolean = false,
  val status: String = if (blocked) "blocked" else "default",
  val blockReason: String? = null,
  val protocol: String = "",
  val dnssec: Boolean? = null,
  val responseTimeMs: Int? = null,
  val deviceModel: String? = null
)

typealias DnsLogItem = DnsLogEntry

data class DomainMetric(
  val domain: String,
  val queries: Long = 0L,
  val queriesCount: Long = queries,
  val percentage: Float = 0f,
  val root: String = ""
)

data class DeviceMetric(
  val id: String = "",
  val name: String,
  val queries: Long = 0L,
  val deviceName: String = name,
  val queriesCount: Long = queries,
  val percentage: Float = 0f,
  val clientIp: String = "",
  val model: String = ""
)

data class ProtocolMetric(
  val protocol: String,
  val queries: Long = 0L
)

data class QueryTypeMetric(
  val type: Int? = null,
  val name: String,
  val queries: Long = 0L
)

data class IpVersionMetric(
  val version: Int,
  val queries: Long = 0L
)

data class IpMetric(
  val ip: String,
  val queries: Long = 0L,
  val cellular: Boolean? = null,
  val vpn: Boolean? = null,
  val isp: String? = null,
  val asn: Long? = null,
  val countryCode: String? = null,
  val country: String? = null,
  val city: String? = null,
  val latitude: Double? = null,
  val longitude: Double? = null
)

data class CountryTrafficMetric(
  val code: String,
  val queries: Long,
  val percentage: Double,
  val domains: List<String> = emptyList()
)

data class AnalyticsSummary(
  val totalQueries: Long = 0L,
  val blockedQueries: Long = 0L,
  val blockRate: Double = 0.0,
  val blockedPercentage: Float = 0f,
  val topAllowedDomains: List<DomainMetric> = emptyList(),
  val topBlockedDomains: List<DomainMetric> = emptyList(),
  val topBlockedReasons: Map<String, Long> = emptyMap(),
  val topDevices: List<DeviceMetric> = emptyList(),
  val topDomains: List<DomainMetric> = emptyList(),
  val protocols: List<ProtocolMetric> = emptyList(),
  val queryTypes: List<QueryTypeMetric> = emptyList(),
  val ipVersions: List<IpVersionMetric> = emptyList(),
  val topIps: List<IpMetric> = emptyList(),
  val gafamMetrics: Map<String, Pair<Double, Long>> = emptyMap(),
  val encryptedDnsPercentage: Float = 0f,
  val dnssecPercentage: Float = 0f,
  val topCountries: List<CountryTrafficMetric> = emptyList()
)



data class RewriteItem(
  val id: String = java.util.UUID.randomUUID().toString(),
  val domain: String,
  val answer: String
)


data class AnalyticsItemDto(
  val id: String? = null,
  val name: String? = null,
  val company: String? = null,
  val queries: Long? = null,
  val validated: Boolean? = null,
  val encrypted: Boolean? = null,
  val code: String? = null,
  val domains: List<String>? = null
)

data class DiagnosticTestResult(
  val status: String = "unconfigured",
  val protocol: String = "",
  val profileId: String = "",
  val clientIp: String = "",
  val resolver: String? = null,
  val serverPoP: String = "",
  val anycast: Boolean? = null,
  val latencyMs: Int = 0,
  val isEncrypted: Boolean = false,
  val isTesting: Boolean = false,
  val lastTestedTime: Long = 0L,
  val errorMessage: String? = null
)

data class ConfigSettings(
  val logsEnabled: Boolean = true,
  val logDomains: Boolean = true,
  val logClientIps: Boolean = false,
  val logRetention: String = "1 ay",
  val logStorageLocation: String = "İsviçre",
  val blockPage: Boolean = false,
  val ednsClientSubnet: Boolean = true,
  val cacheBoost: Boolean = false,
  val cnameFlattening: Boolean = true,
  val web3: Boolean = false,
  val bypassAgeVerification: Boolean = false,
  val rewrites: List<RewriteItem> = emptyList()
)

data class ConfigStatus(
  val status: String = "Yok",
  val clientIp: String? = null
)
