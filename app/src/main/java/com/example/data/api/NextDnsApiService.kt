package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import com.example.data.model.AnalyticsItemDto

// ==========================================
// NextDNS API Response DTOs
// ==========================================

data class NextDnsApiResponse<T>(
  val data: T? = null,
  val errors: List<ApiErrorDetail>? = null,
  val meta: ApiMeta? = null
)

data class ApiErrorDetail(
  val code: String? = null,
  val detail: String? = null,
  val source: ApiErrorSource? = null
)

data class ApiErrorSource(
  val parameter: String? = null,
  val pointer: String? = null
)

data class ApiMeta(
  val pagination: ApiPaginationMeta? = null,
  val stream: ApiStreamMeta? = null
)

data class ApiPaginationMeta(
  val cursor: String? = null
)

data class ApiStreamMeta(
  val id: String? = null
)

data class ProfileCreateDto(
  val id: String
)

data class NextDnsMutationResponse(
  val errors: List<ApiErrorDetail>? = null,
  val meta: ApiMeta? = null
)

data class ProfileDto(
  val id: String,
  val name: String,
  val fingerprint: String? = null
)

data class SecurityDto(
  val threatIntelligenceFeeds: Boolean? = null,
  val aiThreatDetection: Boolean? = null,
  val googleSafeBrowsing: Boolean? = null,
  val cryptojacking: Boolean? = null,
  val dnsRebinding: Boolean? = null,
  val idnHomographs: Boolean? = null,
  val typosquatting: Boolean? = null,
  val dga: Boolean? = null,
  val nrd: Boolean? = null,
  val ddns: Boolean? = null,
  val parking: Boolean? = null,
  val csam: Boolean? = null,
  val tlds: List<TldDto>? = null,
  @Json(ignore = true) val unknownFields: Map<String, Any?> = emptyMap(),
  @Json(ignore = true) val unknownFieldOverflowCount: Int = 0,
  @Json(ignore = true) val unknownFieldOverflowDigest: String = ""
)

data class TldDto(
  val id: String
)

data class BlocklistDto(
  val id: String,
  val name: String? = null,
  val description: String? = null,
  val entries: Long? = null,
  val website: String? = null,
  @Json(name = "updatedOn") val updatedOn: String? = null
)

data class NativeTrackingDto(
  val id: String
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class PrivacyDto(
  val blocklists: List<BlocklistDto>? = null,
  val natives: List<NativeTrackingDto>? = null,
  val disguisedTrackers: Boolean? = null,
  @Json(name = "allowAffiliate") val allowAffiliateLinks: Boolean? = null
)

data class ParentalServiceDto(
  val id: String,
  val active: Boolean? = true
)

data class ParentalCategoryDto(
  val id: String,
  val active: Boolean? = true
)

data class ParentalServiceCatalogDto(
  val id: String,
  val website: String? = null
)

data class SecurityTldCatalogDto(
  val id: String,
  val spamhaus: Int? = 0
)

data class SetupLinkedIpDto(
  val ip: String? = null,
  val servers: List<String>? = null,
  val ddns: String? = null,
  val updateToken: String? = null
)

data class SetupDto(
  val ipv4: List<String>? = null,
  val ipv6: List<String>? = null,
  val linkedIp: SetupLinkedIpDto? = null,
  val dnscrypt: String? = null
)

data class ParentalControlDto(
  val services: List<ParentalServiceDto>? = null,
  val categories: List<ParentalCategoryDto>? = null,
  val safeSearch: Boolean? = null,
  val youtubeRestrictedMode: Boolean? = null,
  val blockBypass: Boolean? = null
)

data class AllowDenyRuleDto(
  val id: String,
  val active: Boolean? = true
)

data class LogReasonDto(
  val id: String? = null,
  val name: String? = null
)

data class DeviceDto(
  val id: String?,
  val name: String?,
  val model: String?
)

data class DnsLogDto(
  val id: String? = null,
  val timestamp: Any? = null,
  val domain: String? = null,
  val root: String? = null,
  val rootDomain: String? = null,
  val tracker: String? = null,
  val encrypted: Boolean? = null,
  val client: String? = null,
  val clientIp: String? = null,
  @Json(name = "client_ip") val clientIpSnake: String? = null,
  val deviceName: String? = null,
  @Json(name = "device_name") val deviceNameSnake: String? = null,
  val device: DeviceDto? = null,
  val status: String? = null,
  val reasons: List<LogReasonDto>? = null,
  val protocol: String? = null,
  val dnssec: Boolean? = null,
  val responseTime: Int? = null,
  @Json(name = "response_time") val responseTimeSnake: Int? = null
)

data class AnalyticsStatusItem(
  val status: String? = null,
  val queries: Long? = null
)

data class AnalyticsDeviceItem(
  val id: String? = null,
  val name: String? = null,
  val model: String? = null,
  val localIp: String? = null,
  val queries: Long? = null
)

data class AnalyticsDomainItem(
  val domain: String? = null,
  val queries: Long? = null,
  val root: String? = null
)

data class AnalyticsReasonItem(
  val id: String? = null,
  val name: String? = null,
  val queries: Long? = null
)

data class AnalyticsProtocolItem(
  val protocol: String? = null,
  val queries: Long? = null
)

data class AnalyticsQueryTypeItem(
  val type: Int? = null,
  val name: String? = null,
  val queries: Long? = null
)

data class AnalyticsIpVersionItem(
  val version: Int? = null,
  val queries: Long? = null
)

data class AnalyticsNetworkDto(
  val cellular: Boolean? = null,
  val vpn: Boolean? = null,
  val isp: String? = null,
  val asn: Long? = null
)

data class AnalyticsGeoDto(
  val latitude: Double? = null,
  val longitude: Double? = null,
  val countryCode: String? = null,
  val country: String? = null,
  val city: String? = null
)

data class AnalyticsIpItem(
  val ip: String? = null,
  val network: AnalyticsNetworkDto? = null,
  val geo: AnalyticsGeoDto? = null,
  val queries: Long? = null
)

data class SettingsDto(
  val logs: SettingsLogsDto? = null,
  val blockPage: SettingsBlockPageDto? = null,
  val performance: SettingsPerformanceDto? = null,
  val web3: Boolean? = null
)

data class SettingsLogsDto(
  val enabled: Boolean? = null,
  val retention: Int? = null,
  val location: String? = null,
  val drop: SettingsLogsDropDto? = null
)

data class SettingsLogsDropDto(
  val ip: Boolean? = null,
  val domain: Boolean? = null
)

data class SettingsBlockPageDto(
  val enabled: Boolean? = null
)

data class SettingsPerformanceDto(
  val ecs: Boolean? = null,
  val cacheBoost: Boolean? = null,
  val cnameFlattening: Boolean? = null
)

data class NextDnsTestResponse(
  val status: String? = null,
  val protocol: String? = null,
  val profile: String? = null,
  val client: String? = null,
  @Json(name = "srcIP") val srcIP: String? = null,
  val resolver: String? = null,
  val server: String? = null,
  val anycast: Boolean? = null
)

// ==========================================
// Strongly-Typed Request DTOs for Mutations
// ==========================================

data class NameRequest(
  val name: String
)

data class IdRequest(
  val id: String
)

data class SecurityUpdateRequest(
  val threatIntelligenceFeeds: Boolean? = null,
  val aiThreatDetection: Boolean? = null,
  val googleSafeBrowsing: Boolean? = null,
  val cryptojacking: Boolean? = null,
  val dnsRebinding: Boolean? = null,
  val idnHomographs: Boolean? = null,
  val typosquatting: Boolean? = null,
  val dga: Boolean? = null,
  val nrd: Boolean? = null,
  val ddns: Boolean? = null,
  val parking: Boolean? = null,
  val csam: Boolean? = null
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class PrivacyUpdateRequest(
  val disguisedTrackers: Boolean? = null,
  @Json(name = "allowAffiliate") val allowAffiliateLinks: Boolean? = null
)

data class ParentalControlUpdateRequest(
  val safeSearch: Boolean? = null,
  val youtubeRestrictedMode: Boolean? = null,
  val blockBypass: Boolean? = null
)

data class ParentalItemRequest(
  val id: String,
  val active: Boolean? = true
)

data class ParentalActiveRequest(
  val active: Boolean
)

data class AllowDenyItemRequest(
  val id: String,
  val active: Boolean? = true
)

data class AllowDenyActiveRequest(
  val active: Boolean
)

data class SettingsUpdateRequest(
  val logs: SettingsLogsDto? = null,
  val blockPage: SettingsBlockPageDto? = null,
  val performance: SettingsPerformanceDto? = null,
  val web3: Boolean? = null
)

data class SettingsPerformanceUpdateRequest(
  val ecs: Boolean? = null,
  val cacheBoost: Boolean? = null,
  val cnameFlattening: Boolean? = null
)

data class SettingsLogsUpdateRequest(
  val enabled: Boolean? = null,
  val retention: Int? = null,
  val location: String? = null,
  val drop: SettingsLogsDropDto? = null
)

data class SettingsBlockPageUpdateRequest(
  val enabled: Boolean? = null
)

// ==========================================
// NextDNS Retrofit Service Interface
// ==========================================

interface NextDnsApiService {

  // Profiles
  @GET("profiles")
  suspend fun getProfiles(
    @Header("X-Api-Key") apiKey: String,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<ProfileDto>>>

  @POST("profiles")
  suspend fun createProfile(
    @Header("X-Api-Key") apiKey: String,
    @Body body: NameRequest
  ): Response<NextDnsApiResponse<ProfileCreateDto>>

  @GET("profiles/{profileId}")
  suspend fun getProfile(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String
  ): Response<NextDnsApiResponse<ProfileDto>>

  @DELETE("profiles/{profileId}")
  suspend fun deleteProfile(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String
  ): Response<NextDnsMutationResponse>

  @PATCH("profiles/{profileId}")
  suspend fun renameProfile(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: NameRequest
  ): Response<NextDnsMutationResponse>

  // Setup
  @GET("profiles/{profileId}/setup")
  suspend fun getProfileSetup(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String
  ): Response<SetupDto>

  @POST("profiles/{profileId}/setup/linkedIp")
  suspend fun updateLinkedIp(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: Map<String, String?> = emptyMap()
  ): Response<ResponseBody>

  // Security
  @GET("profiles/{profileId}/security")
  suspend fun getSecurity(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String
  ): Response<NextDnsApiResponse<SecurityDto>>

  @GET("security/tlds")
  suspend fun getAvailableTlds(
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<SecurityTldCatalogDto>>>

  @PATCH("profiles/{profileId}/security")
  suspend fun updateSecurity(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: SecurityUpdateRequest
  ): Response<NextDnsMutationResponse>

  @POST("profiles/{profileId}/security/tlds")
  suspend fun addSecurityTld(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: IdRequest
  ): Response<NextDnsMutationResponse>

  @DELETE("profiles/{profileId}/security/tlds/{tld}")
  suspend fun removeSecurityTld(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("tld") tld: String
  ): Response<NextDnsMutationResponse>

  // Privacy
  @GET("profiles/{profileId}/privacy")
  suspend fun getPrivacy(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String
  ): Response<NextDnsApiResponse<PrivacyDto>>

  @GET("profiles/{profileId}/privacy/blocklists")
  suspend fun getProfileBlocklists(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<BlocklistDto>>>

  @GET("profiles/{profileId}/privacy/natives")
  suspend fun getProfileNatives(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<NativeTrackingDto>>>

  @GET("privacy/blocklists")
  suspend fun getAvailableBlocklists(
    @Header("X-Api-Key") apiKey: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<BlocklistDto>>>

  @GET("privacy/natives")
  suspend fun getAvailableNatives(
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<NativeTrackingDto>>>

  @PATCH("profiles/{profileId}/privacy")
  suspend fun updatePrivacy(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: PrivacyUpdateRequest
  ): Response<NextDnsMutationResponse>

  @POST("profiles/{profileId}/privacy/blocklists")
  suspend fun addBlocklist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: IdRequest
  ): Response<NextDnsMutationResponse>

  @DELETE("profiles/{profileId}/privacy/blocklists/{blocklistId}")
  suspend fun removeBlocklist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("blocklistId") blocklistId: String
  ): Response<NextDnsMutationResponse>

  @POST("profiles/{profileId}/privacy/natives")
  suspend fun addNativeTracking(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: IdRequest
  ): Response<NextDnsMutationResponse>

  @DELETE("profiles/{profileId}/privacy/natives/{nativeId}")
  suspend fun removeNativeTracking(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("nativeId") nativeId: String
  ): Response<NextDnsMutationResponse>

  // Parental Control
  @GET("parentalControl/services")
  suspend fun getAvailableParentalServices(
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<ParentalServiceCatalogDto>>>

  @GET("parentalControl/categories")
  suspend fun getAvailableParentalCategories(
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<ParentalCategoryDto>>>

  @GET("profiles/{profileId}/parentalControl")
  suspend fun getParentalControl(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String
  ): Response<NextDnsApiResponse<ParentalControlDto>>

  @PATCH("profiles/{profileId}/parentalControl")
  suspend fun updateParentalControl(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: ParentalControlUpdateRequest
  ): Response<NextDnsMutationResponse>

  @POST("profiles/{profileId}/parentalControl/categories")
  suspend fun addParentalCategory(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: ParentalItemRequest
  ): Response<NextDnsMutationResponse>

  @PATCH("profiles/{profileId}/parentalControl/categories/{categoryId}")
  suspend fun updateParentalCategory(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("categoryId") categoryId: String,
    @Body body: ParentalActiveRequest
  ): Response<NextDnsMutationResponse>

  @DELETE("profiles/{profileId}/parentalControl/categories/{categoryId}")
  suspend fun removeParentalCategory(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("categoryId") categoryId: String
  ): Response<NextDnsMutationResponse>

  @POST("profiles/{profileId}/parentalControl/services")
  suspend fun addParentalService(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: ParentalItemRequest
  ): Response<NextDnsMutationResponse>

  @PATCH("profiles/{profileId}/parentalControl/services/{serviceId}")
  suspend fun updateParentalService(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("serviceId") serviceId: String,
    @Body body: ParentalActiveRequest
  ): Response<NextDnsMutationResponse>

  @DELETE("profiles/{profileId}/parentalControl/services/{serviceId}")
  suspend fun removeParentalService(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("serviceId") serviceId: String
  ): Response<NextDnsMutationResponse>

  // Denylist
  @GET("profiles/{profileId}/denylist")
  suspend fun getDenylist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AllowDenyRuleDto>>>

  @POST("profiles/{profileId}/denylist")
  suspend fun addDenylist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: AllowDenyItemRequest
  ): Response<NextDnsMutationResponse>

  @PATCH("profiles/{profileId}/denylist/{domain}")
  suspend fun toggleDenylist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("domain") domain: String,
    @Body body: AllowDenyActiveRequest
  ): Response<NextDnsMutationResponse>

  @DELETE("profiles/{profileId}/denylist/{domain}")
  suspend fun removeDenylist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("domain") domain: String
  ): Response<NextDnsMutationResponse>

  // Allowlist
  @GET("profiles/{profileId}/allowlist")
  suspend fun getAllowlist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AllowDenyRuleDto>>>

  @POST("profiles/{profileId}/allowlist")
  suspend fun addAllowlist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: AllowDenyItemRequest
  ): Response<NextDnsMutationResponse>

  @PATCH("profiles/{profileId}/allowlist/{domain}")
  suspend fun toggleAllowlist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("domain") domain: String,
    @Body body: AllowDenyActiveRequest
  ): Response<NextDnsMutationResponse>

  @DELETE("profiles/{profileId}/allowlist/{domain}")
  suspend fun removeAllowlist(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Path("domain") domain: String
  ): Response<NextDnsMutationResponse>

  // Logs
  @Streaming
  @GET("profiles/{profileId}/logs/stream")
  suspend fun getLogsStream(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("search") search: String? = null,
    @Query("device") device: String? = null,
    @Query("status") status: String? = null,
    @Query("raw") raw: Int? = 1
  ): retrofit2.Response<okhttp3.ResponseBody>

  @GET("profiles/{profileId}/logs")
  suspend fun getLogs(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("limit") limit: Int = 100,
    @Query("search") search: String? = null,
    @Query("device") device: String? = null,
    @Query("status") status: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("sort") sort: String? = null,
    @Query("cursor") cursor: String? = null,
    @Query("raw") raw: Int? = 1
  ): Response<NextDnsApiResponse<List<DnsLogDto>>>

  @GET("profiles/{profileId}/logs/download")
  suspend fun getLogsDownloadLink(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("redirect") redirect: Int = 0
  ): Response<ResponseBody>

  @DELETE("profiles/{profileId}/logs")
  suspend fun clearLogs(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String
  ): Response<NextDnsMutationResponse>

  // Analytics
  @GET("profiles/{profileId}/analytics/status")
  suspend fun getAnalyticsStatus(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsStatusItem>>>

  @GET("profiles/{profileId}/analytics/devices")
  suspend fun getAnalyticsDevices(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsDeviceItem>>>

  @GET("profiles/{profileId}/analytics/domains")
  suspend fun getAnalyticsDomains(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("status") status: String? = null,
    @Query("root") root: Boolean? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsDomainItem>>>

  @GET("profiles/{profileId}/analytics/reasons")
  suspend fun getAnalyticsReasons(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsReasonItem>>>


  @GET("profiles/{profileId}/analytics/destinations")
  suspend fun getAnalyticsDestinations(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("type") type: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsItemDto>>>

  @GET("profiles/{profileId}/analytics/dnssec")
  suspend fun getAnalyticsDnssec(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsItemDto>>>

  @GET("profiles/{profileId}/analytics/encryption")
  suspend fun getAnalyticsEncryption(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsItemDto>>>

  @GET("profiles/{profileId}/analytics/protocols")
  suspend fun getAnalyticsProtocols(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsProtocolItem>>>

  @GET("profiles/{profileId}/analytics/ips")
  suspend fun getAnalyticsIps(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsIpItem>>>

  @GET("profiles/{profileId}/analytics/queryTypes")
  suspend fun getAnalyticsQueryTypes(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsQueryTypeItem>>>

  @GET("profiles/{profileId}/analytics/ipVersions")
  suspend fun getAnalyticsIpVersions(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Query("device") device: String? = null,
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
    @Query("limit") limit: Int? = null,
    @Query("cursor") cursor: String? = null
  ): Response<NextDnsApiResponse<List<AnalyticsIpVersionItem>>>

  // Settings
  @GET("profiles/{profileId}/settings")
  suspend fun getSettings(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String
  ): Response<NextDnsApiResponse<SettingsDto>>

  @PATCH("profiles/{profileId}/settings")
  suspend fun updateSettings(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: SettingsUpdateRequest
  ): Response<NextDnsMutationResponse>

  @PATCH("profiles/{profileId}/settings/performance")
  suspend fun updateSettingsPerformance(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: SettingsPerformanceUpdateRequest
  ): Response<NextDnsMutationResponse>

  @PATCH("profiles/{profileId}/settings/logs")
  suspend fun updateSettingsLogs(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: SettingsLogsUpdateRequest
  ): Response<NextDnsMutationResponse>

  @PATCH("profiles/{profileId}/settings/blockPage")
  suspend fun updateSettingsBlockPage(
    @Header("X-Api-Key") apiKey: String,
    @Path("profileId") profileId: String,
    @Body body: SettingsBlockPageUpdateRequest
  ): Response<NextDnsMutationResponse>
}

interface NextDnsTestService {
  @GET("/")
  suspend fun testConnection(): Response<NextDnsTestResponse>
}

object NextDnsNetworkClient {
  private const val BASE_URL = "https://api.nextdns.io/"
  private const val TEST_URL = "https://test.nextdns.io/"

  private val logging = HttpLoggingInterceptor().apply {
    redactHeader("X-Api-Key")
    level = if (BuildConfig.DEBUG) {
      HttpLoggingInterceptor.Level.BASIC
    } else {
      HttpLoggingInterceptor.Level.NONE
    }
  }

  val client = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .addInterceptor(SecurityResponseSizeInterceptor())
    .addInterceptor { chain ->
      val original = chain.request()
      val request = original.newBuilder()
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 NextDNS-Android")
        .build()
      chain.proceed(request)
    }
    .addInterceptor(logging)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

  // NextDNS supplies a public HTTPS URL for log exports. Never follow a
  // redirect that downgrades the transfer from HTTPS to unencrypted HTTP.
  val publicDownloadClient: OkHttpClient = OkHttpClient.Builder()
    .followSslRedirects(false)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  val moshi = Moshi.Builder()
    .add(SecurityDtoJsonAdapterFactory())
    .addLast(KotlinJsonAdapterFactory())
    .build()

  val api: NextDnsApiService by lazy {
    Retrofit.Builder()
      .baseUrl(BASE_URL)
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(NextDnsApiService::class.java)
  }

  val testApi: NextDnsTestService by lazy {
    Retrofit.Builder()
      .baseUrl(TEST_URL)
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
      .build()
      .create(NextDnsTestService::class.java)
  }

  suspend fun fetchTestConnectionDirect(profileId: String? = null, callFactory: okhttp3.Call.Factory = client): NextDnsTestResponse? {
    return try {
      val testUrl = if (!profileId.isNullOrBlank()) {
        val rand = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        "https://$rand-$profileId.test.nextdns.io/"
      } else {
        TEST_URL
      }
      val request = Request.Builder()
        .url(testUrl)
        .header("Accept", "application/json")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) OpenSourceClientForNextDNS/1.0")
        .build()
      callFactory.newCall(request).withCancellableResponse { response ->
        if (!response.isSuccessful) return@withCancellableResponse null
        val bodyStr = response.body?.string() ?: return@withCancellableResponse null
        val json = org.json.JSONObject(bodyStr)
        NextDnsTestResponse(
          status = json.optString("status").takeIf { it.isNotBlank() },
          protocol = json.optString("protocol").takeIf { it.isNotBlank() },
          profile = json.optString("profile").takeIf { it.isNotBlank() },
          client = json.optString("client").takeIf { it.isNotBlank() },
          srcIP = json.optString("srcIP").takeIf { it.isNotBlank() },
          resolver = json.optString("resolver").takeIf { it.isNotBlank() },
          server = json.optString("server").takeIf { it.isNotBlank() },
          anycast = if (json.has("anycast")) json.optBoolean("anycast") else null
        )
      }
    } catch (cancel: kotlinx.coroutines.CancellationException) {
      throw cancel
    } catch (_: Exception) {
      null
    }
  }

  suspend fun linkIpAddress(profileId: String, callFactory: okhttp3.Call.Factory = client): Boolean {
    return try {
      val request = Request.Builder()
        .url("https://link-ip.nextdns.io/$profileId")
        .header("Accept", "*/*")
        .build()
      callFactory.newCall(request).withCancellableResponse { it.isSuccessful }
    } catch (cancel: kotlinx.coroutines.CancellationException) {
      throw cancel
    } catch (_: Exception) {
      false
    }
  }

  suspend fun fetchAvailableBlocklistsDirect(callFactory: okhttp3.Call.Factory = client): List<BlocklistDto>? {
    return try {
      val request = Request.Builder()
        .url("https://api.nextdns.io/privacy/blocklists")
        .header("Accept", "application/json")
        .header("User-Agent", "NextDNS-Android/1.0")
        .build()
      callFactory.newCall(request).withCancellableResponse { response ->
        if (!response.isSuccessful) return@withCancellableResponse null
        val bodyStr = response.body?.string() ?: return@withCancellableResponse null
        val json = org.json.JSONObject(bodyStr)
        val arr = json.optJSONArray("data") ?: return@withCancellableResponse null
        val list = mutableListOf<BlocklistDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            BlocklistDto(
              id = obj.getString("id"),
              name = obj.optString("name").takeIf { it.isNotBlank() && it != "null" },
              description = obj.optString("description").takeIf { it.isNotBlank() && it != "null" },
              entries = if (obj.has("entries") && !obj.isNull("entries")) obj.getLong("entries") else null,
              website = obj.optString("website").takeIf { it.isNotBlank() && it != "null" },
              updatedOn = obj.optString("updatedOn").takeIf { it.isNotBlank() && it != "null" }
            )
          )
        }
        list
      }
    } catch (cancel: kotlinx.coroutines.CancellationException) {
      throw cancel
    } catch (_: Exception) {
      null
    }
  }

  suspend fun fetchAvailableNativesDirect(callFactory: okhttp3.Call.Factory = client): List<NativeTrackingDto>? {
    return try {
      val request = Request.Builder()
        .url("https://api.nextdns.io/privacy/natives")
        .header("Accept", "application/json")
        .build()
      callFactory.newCall(request).withCancellableResponse { response ->
        if (!response.isSuccessful) return@withCancellableResponse null
        val bodyStr = response.body?.string() ?: return@withCancellableResponse null
        val json = org.json.JSONObject(bodyStr)
        val arr = json.optJSONArray("data") ?: return@withCancellableResponse null
        val list = mutableListOf<NativeTrackingDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(NativeTrackingDto(id = obj.getString("id")))
        }
        list
      }
    } catch (cancel: kotlinx.coroutines.CancellationException) {
      throw cancel
    } catch (_: Exception) {
      null
    }
  }

  suspend fun fetchAvailableParentalServicesDirect(callFactory: okhttp3.Call.Factory = client): List<ParentalServiceCatalogDto>? {
    return try {
      val request = Request.Builder()
        .url("https://api.nextdns.io/parentalControl/services")
        .header("Accept", "application/json")
        .build()
      callFactory.newCall(request).withCancellableResponse { response ->
        if (!response.isSuccessful) return@withCancellableResponse null
        val bodyStr = response.body?.string() ?: return@withCancellableResponse null
        val json = org.json.JSONObject(bodyStr)
        val arr = json.optJSONArray("data") ?: return@withCancellableResponse null
        val list = mutableListOf<ParentalServiceCatalogDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            ParentalServiceCatalogDto(
              id = obj.getString("id"),
              website = obj.optString("website").takeIf { it.isNotBlank() && it != "null" }
            )
          )
        }
        list
      }
    } catch (cancel: kotlinx.coroutines.CancellationException) {
      throw cancel
    } catch (_: Exception) {
      null
    }
  }

  suspend fun fetchAvailableParentalCategoriesDirect(callFactory: okhttp3.Call.Factory = client): List<ParentalCategoryDto>? {
    return try {
      val request = Request.Builder()
        .url("https://api.nextdns.io/parentalControl/categories")
        .header("Accept", "application/json")
        .build()
      callFactory.newCall(request).withCancellableResponse { response ->
        if (!response.isSuccessful) return@withCancellableResponse null
        val bodyStr = response.body?.string() ?: return@withCancellableResponse null
        val json = org.json.JSONObject(bodyStr)
        val arr = json.optJSONArray("data") ?: return@withCancellableResponse null
        val list = mutableListOf<ParentalCategoryDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(ParentalCategoryDto(id = obj.getString("id")))
        }
        list
      }
    } catch (cancel: kotlinx.coroutines.CancellationException) {
      throw cancel
    } catch (_: Exception) {
      null
    }
  }

  suspend fun fetchAvailableTldsDirect(callFactory: okhttp3.Call.Factory = client): List<SecurityTldCatalogDto>? {
    return try {
      val request = Request.Builder()
        .url("https://api.nextdns.io/security/tlds")
        .header("Accept", "application/json")
        .build()
      callFactory.newCall(request).withCancellableResponse { response ->
        if (!response.isSuccessful) return@withCancellableResponse null
        val bodyStr = response.body?.string() ?: return@withCancellableResponse null
        val json = org.json.JSONObject(bodyStr)
        val arr = json.optJSONArray("data") ?: return@withCancellableResponse null
        val list = mutableListOf<SecurityTldCatalogDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SecurityTldCatalogDto(
              id = obj.getString("id"),
              spamhaus = if (obj.has("spamhaus")) obj.optInt("spamhaus", 0) else 0
            )
          )
        }
        list
      }
    } catch (cancel: kotlinx.coroutines.CancellationException) {
      throw cancel
    } catch (_: Exception) {
      null
    }
  }
}
