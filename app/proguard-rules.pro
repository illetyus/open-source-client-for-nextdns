# Reflection-only Moshi API and existing persisted-JSON models.
# Preserve names, constructors and Kotlin metadata; do not keep the UI or repository.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# AndroidJUnitRunner calls this shared dependency after app shrinking. Its
# provided test classpath does not package a second copy, so the public API must
# survive in the real release payload. Keep this one entry point, not the SDK.
-keep class androidx.tracing.Trace { public *; }

# Optional JVM-only TLS providers, guarded by OkHttp Platform availability checks.
# Narrower than Square's official consumer rules in parent-4.12.0/okhttp/.../okhttp3.pro.
# Android's built-in TLS provider and certificate verification are unchanged.
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

-keep class com.example.data.api.AllowDenyActiveRequest { *; }
-keep class com.example.data.api.AllowDenyItemRequest { *; }
-keep class com.example.data.api.AllowDenyRuleDto { *; }
-keep class com.example.data.api.AnalyticsDeviceItem { *; }
-keep class com.example.data.api.AnalyticsDomainItem { *; }
-keep class com.example.data.api.AnalyticsGeoDto { *; }
-keep class com.example.data.api.AnalyticsIpItem { *; }
-keep class com.example.data.api.AnalyticsIpVersionItem { *; }
-keep class com.example.data.api.AnalyticsNetworkDto { *; }
-keep class com.example.data.api.AnalyticsProtocolItem { *; }
-keep class com.example.data.api.AnalyticsQueryTypeItem { *; }
-keep class com.example.data.api.AnalyticsReasonItem { *; }
-keep class com.example.data.api.AnalyticsSeriesDto { *; }
-keep class com.example.data.api.AnalyticsStatusItem { *; }
-keep class com.example.data.api.ApiErrorDetail { *; }
-keep class com.example.data.api.ApiErrorSource { *; }
-keep class com.example.data.api.ApiMeta { *; }
-keep class com.example.data.api.ApiPaginationMeta { *; }
-keep class com.example.data.api.ApiSeriesMeta { *; }
-keep class com.example.data.api.ApiStreamMeta { *; }
-keep class com.example.data.api.BlocklistDto { *; }
-keep class com.example.data.api.DeviceDto { *; }
-keep class com.example.data.api.DnsLogDto { *; }
-keep class com.example.data.api.IdRequest { *; }
-keep class com.example.data.api.LogReasonDto { *; }
-keep class com.example.data.api.NameRequest { *; }
-keep class com.example.data.api.NativeTrackingDto { *; }
-keep class com.example.data.api.NextDnsApiResponse { *; }
-keep class com.example.data.api.NextDnsMutationResponse { *; }
-keep class com.example.data.api.NextDnsTestResponse { *; }
-keep class com.example.data.api.ParentalActiveRequest { *; }
-keep class com.example.data.api.ParentalCategoryDto { *; }
-keep class com.example.data.api.ParentalControlDto { *; }
-keep class com.example.data.api.ParentalControlUpdateRequest { *; }
-keep class com.example.data.api.ParentalItemRequest { *; }
-keep class com.example.data.api.ParentalServiceCatalogDto { *; }
-keep class com.example.data.api.ParentalServiceDto { *; }
-keep class com.example.data.api.PrivacyDto { *; }
-keep class com.example.data.api.PrivacyUpdateRequest { *; }
-keep class com.example.data.api.ProfileCreateDto { *; }
-keep class com.example.data.api.ProfileDto { *; }
-keep class com.example.data.api.SecurityDto { *; }
-keep class com.example.data.api.SecurityFieldDiscovery { *; }
-keep class com.example.data.api.SecurityTldCatalogDto { *; }
-keep class com.example.data.api.SecurityUpdateRequest { *; }
-keep class com.example.data.api.SettingsBlockPageDto { *; }
-keep class com.example.data.api.SettingsBlockPageUpdateRequest { *; }
-keep class com.example.data.api.SettingsDto { *; }
-keep class com.example.data.api.SettingsLogsDropDto { *; }
-keep class com.example.data.api.SettingsLogsDto { *; }
-keep class com.example.data.api.SettingsLogsUpdateRequest { *; }
-keep class com.example.data.api.SettingsPerformanceDto { *; }
-keep class com.example.data.api.SettingsPerformanceUpdateRequest { *; }
-keep class com.example.data.api.SettingsUpdateRequest { *; }
-keep class com.example.data.api.TldDto { *; }
-keep class com.example.data.api.UnverifiedSecurityField { *; }

-keep class com.example.data.model.NextDnsProfile { *; }
-keep class com.example.data.model.SecuritySettings { *; }
-keep class com.example.data.model.PrivacySettings { *; }
-keep class com.example.data.model.BlocklistEntry { *; }
-keep class com.example.data.model.NativeTrackingEntry { *; }
-keep class com.example.data.model.ParentalControlSettings { *; }
-keep class com.example.data.model.BlockedServiceEntry { *; }
-keep class com.example.data.model.BlockedCategoryEntry { *; }
-keep class com.example.data.model.AllowDenyItem { *; }
-keep class com.example.data.model.ConfigSettings { *; }
-keep class com.example.data.model.RewriteItem { *; }
-keep class com.example.data.model.AnalyticsItemDto { *; }
