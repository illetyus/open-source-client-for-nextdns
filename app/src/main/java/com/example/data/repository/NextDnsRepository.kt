package com.example.data.repository

import com.example.R
import com.example.i18n.AppStrings

import android.util.Log
import com.example.BuildConfig
import com.example.NextDnsApp
import com.example.data.api.*
import com.example.data.local.NextDnsPreferences
import com.example.data.model.*
import com.example.data.notifications.NotificationWorkScheduler
import com.example.data.notifications.NotificationPreferences
import com.example.data.legal.LegalAcceptanceStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import com.example.data.api.withCancellableResponse
import java.io.OutputStream
import java.util.UUID
import java.util.Locale
import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Response

private const val MAX_RETAINED_LOG_ENTRIES = 1_000

sealed interface ApiConnectionStatus {
  object Disconnected : ApiConnectionStatus
  object Connecting : ApiConnectionStatus
  data class Connected(val profileCount: Int) : ApiConnectionStatus
  data class Error(val message: String) : ApiConnectionStatus
}

private class ProfilesFetchException(
  val httpCode: Int,
  message: String
) : Exception(message)

private class SessionSnapshot(val generation: Long, val apiKey: String, val profileId: String)
private class AnalyticsRequest(val session: SessionSnapshot, val device: String?, val from: String?)
private class SessionContext(val snapshot: SessionSnapshot, var owner: Job? = null) :
  AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<SessionContext>
}
private class RefreshContext(val section: SyncSection, val token: Long) :
  AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<RefreshContext>
}

class NextDnsRepository(
  private val preferences: NextDnsPreferences = NextDnsApp.preferences,
  private val apiService: NextDnsApiService = NextDnsNetworkClient.api,
  private val diagnosticService: NextDnsTestService = NextDnsNetworkClient.testApi,
  private val diagnosticProbe: suspend (String?) -> NextDnsTestResponse? = { NextDnsNetworkClient.fetchTestConnectionDirect(it) },
  private val downloadClient: okhttp3.OkHttpClient = NextDnsNetworkClient.publicDownloadClient,
  private val streamClient: okhttp3.OkHttpClient = NextDnsNetworkClient.client,
  private val backgroundWorkEnabled: Boolean = true
) {
  private val TAG = "NextDnsRepo"
  // Only state/cache commits and session transitions hold this lock, never network I/O.
  private val sessionLock = Any()
  private var sessionGeneration = 0L
  private val sessionJobs = mutableSetOf<Job>()
  private val requestSequence = java.util.concurrent.atomic.AtomicLong()
  private val refreshRequests = mutableMapOf<SyncSection, Long>()
  private var fullSyncRequest: Long? = null
  private var diagnosticRequest: Long? = null
  private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val legalAcceptanceStore = LegalAcceptanceStore(NextDnsApp.instance)
  private val startupInitialized = AtomicBoolean(false)

  fun termsAccepted(): Boolean = legalAcceptanceStore.isAccepted()
  private var streamJob: Job? = null
  private var analyticsPollingJob: Job? = null
  private val mutationCoordinator = SectionMutationCoordinator()
  @Volatile private var logsStreamSeedId: String? = null
  @Volatile private var nextLogsCursor: String? = null

  private val _apiKey = MutableStateFlow(preferences.apiKey)
  val apiKey = _apiKey.asStateFlow()

  private val _apiStatus = MutableStateFlow<ApiConnectionStatus>(
    if (legalAcceptanceStore.isAccepted() && preferences.apiKey.isNotBlank()) ApiConnectionStatus.Connecting else ApiConnectionStatus.Disconnected
  )
  val apiStatus = _apiStatus.asStateFlow()

  private val _profiles = MutableStateFlow<List<NextDnsProfile>>(preferences.getProfiles() ?: emptyList())
  val profiles = _profiles.asStateFlow()

  private val _activeProfileId = MutableStateFlow(preferences.activeProfileId)
  val activeProfileId = _activeProfileId.asStateFlow()

  private val _securitySettings = MutableStateFlow(SecuritySettings())
  val securitySettings = _securitySettings.asStateFlow()

  private val _privacySettings = MutableStateFlow(PrivacySettings())
  val privacySettings = _privacySettings.asStateFlow()

  private val _parentalControlSettings = MutableStateFlow(ParentalControlSettings())
  val parentalControlSettings = _parentalControlSettings.asStateFlow()

  private val _denylist = MutableStateFlow<List<AllowDenyItem>>(emptyList())
  val denylist = _denylist.asStateFlow()

  private val _allowlist = MutableStateFlow<List<AllowDenyItem>>(emptyList())
  val allowlist = _allowlist.asStateFlow()

  private val _logs = MutableStateFlow<List<DnsLogEntry>>(emptyList())
  val logs = _logs.asStateFlow()

  private val _logsPaginationState = MutableStateFlow(LogsPaginationState())
  val logsPaginationState = _logsPaginationState.asStateFlow()

  private val _logsDeviceFilter = MutableStateFlow<String?>(null)
  val logsDeviceFilter = _logsDeviceFilter.asStateFlow()
  private val _logsStatusFilter = MutableStateFlow<String?>(null)
  val logsStatusFilter = _logsStatusFilter.asStateFlow()
  private val _logsSearchFilter = MutableStateFlow<String?>(null)
  val logsSearchFilter = _logsSearchFilter.asStateFlow()

  private val _analytics = MutableStateFlow(AnalyticsSummary())
  val analytics = _analytics.asStateFlow()

  private val _analyticsLastSuccessAt = MutableStateFlow<Long?>(null)
  val analyticsLastSuccessAt = _analyticsLastSuccessAt.asStateFlow()

  private val _analyticsErrorMessage = MutableStateFlow<String?>(null)
  val analyticsErrorMessage = _analyticsErrorMessage.asStateFlow()

  private val _knownDeviceNameToId = java.util.concurrent.ConcurrentHashMap<String, String>()
  private val _knownDeviceIdToName = java.util.concurrent.ConcurrentHashMap<String, String>()

  private val _allKnownDevices = MutableStateFlow<List<String>>(emptyList())
  val allKnownDevices = _allKnownDevices.asStateFlow()

  private val _logDevices = MutableStateFlow<List<DeviceMetric>>(emptyList())
  val logDevices = _logDevices.asStateFlow()

  private var analyticsRequest: AnalyticsRequest? = null
  private var logsRefreshRequest: Long? = null
  private var logsSnapshotGeneration = 0L

  fun updateKnownDevices(newDevices: List<String>) {
    val valid = newDevices.filter { it.isNotBlank() && it != "Bilinmeyen" && it != "Cihaz" && it != "Bilinmeyen Cihaz" }
    if (valid.isNotEmpty()) {
      val current = _allKnownDevices.value.toMutableSet()
      current.addAll(valid)
      _allKnownDevices.value = current.toList()
    }
  }

  private fun rememberLogDevice(deviceId: String?, deviceName: String?, deviceModel: String? = null) {
    val id = deviceId?.takeIf { it.isNotBlank() && it != NEXTDNS_UNIDENTIFIED_DEVICE_ID } ?: return
    val name = DeviceLabelResolver.resolve(
      deviceName,
      deviceModel,
      id,
      AppStrings.get(R.string.device_unidentified)
    )
    _knownDeviceNameToId[name] = id
    _knownDeviceIdToName[id] = name
    _logDevices.update { current ->
      (current.filterNot { it.id == id } + DeviceMetric(id = id, name = name, model = deviceModel.orEmpty()))
        .sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    updateKnownDevices(listOf(name))
  }

  private val _configSettings = MutableStateFlow(ConfigSettings())
  val configSettings = _configSettings.asStateFlow()

  private val _testResult = MutableStateFlow(DiagnosticTestResult())
  val testResult = _testResult.asStateFlow()

  private val _isLiveStreaming = MutableStateFlow(false)
  val isLiveStreaming = _isLiveStreaming.asStateFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing = _isSyncing.asStateFlow()

  private val _sectionSyncStates = MutableStateFlow(
    SyncSection.values().associateWith { SectionSyncState() }
  )
  val sectionSyncStates = _sectionSyncStates.asStateFlow()

  private fun resetProfileScopedRuntimeState() {
    stopLogsStream()
    logsStreamSeedId = null
    nextLogsCursor = null
    _logsDeviceFilter.value = null
    _logsStatusFilter.value = null
    _logsPaginationState.value = LogsPaginationState()
    analyticsRequest = null
    logsRefreshRequest = null
    logsSnapshotGeneration++
    _analytics.value = AnalyticsSummary()
    _analyticsLastSuccessAt.value = null
    _analyticsErrorMessage.value = null
    _knownDeviceNameToId.clear()
    _knownDeviceIdToName.clear()
    _allKnownDevices.value = emptyList()
    _logDevices.value = emptyList()
    _sectionSyncStates.value = SyncSection.values().associateWith { SectionSyncState() }
    refreshRequests.clear()
    fullSyncRequest = null
    _isSyncing.value = false
    _profileSetup.value = SetupDto()
    _testResult.value = DiagnosticTestResult()
    diagnosticRequest = null
  }

  private fun captureSession(): SessionSnapshot = synchronized(sessionLock) {
    SessionSnapshot(sessionGeneration, _apiKey.value, _activeProfileId.value)
  }

  private fun isCurrentSession(snapshot: SessionSnapshot): Boolean =
    snapshot.generation == sessionGeneration && snapshot.apiKey == _apiKey.value &&
      snapshot.profileId == _activeProfileId.value

  private fun requireCurrentSession(snapshot: SessionSnapshot) {
    if (!isCurrentSession(snapshot)) throw CancellationException("Repository session changed")
  }

  private fun advanceSession(owner: Job? = null) {
    sessionGeneration++
    sessionJobs.filter { it !== owner }.forEach { it.cancel() }
  }

  private suspend fun ensureOperationActive() {
    val context = currentCoroutineContext()
    context.ensureActive()
    synchronized(sessionLock) {
      context[SessionContext]?.let { requireCurrentSession(it.snapshot) }
      context[RefreshContext]?.let {
        if (refreshRequests[it.section] != it.token) throw CancellationException("Refresh superseded")
      }
    }
  }

  private suspend fun <T> commitCurrentSession(block: () -> T): T {
    val context = currentCoroutineContext()
    context.ensureActive()
    return synchronized(sessionLock) {
      requireCurrentSession(checkNotNull(context[SessionContext]).snapshot)
      context[RefreshContext]?.let {
        if (refreshRequests[it.section] != it.token) throw CancellationException("Refresh superseded")
      }
      block()
    }
  }

  private suspend fun <T> withSessionContext(
    expectedKey: String? = null,
    expectedProfile: String? = null,
    snapshot: SessionSnapshot = captureSession(),
    block: suspend () -> T
  ): T {
    if ((expectedKey != null && snapshot.apiKey != expectedKey) ||
      (expectedProfile != null && snapshot.profileId != expectedProfile)) {
      throw CancellationException("Request belongs to another session")
    }
    val inheritedOwner = currentCoroutineContext()[SessionContext]?.owner
    val context = SessionContext(snapshot, inheritedOwner)
    return withContext(Dispatchers.IO + context) {
      val owner = inheritedOwner ?: currentCoroutineContext().job.also { context.owner = it }
      synchronized(sessionLock) {
        requireCurrentSession(snapshot)
        if (inheritedOwner == null) sessionJobs.add(owner)
      }
      try {
        ensureOperationActive()
        block()
      } finally {
        if (inheritedOwner == null) synchronized(sessionLock) { sessionJobs.remove(owner) }
      }
    }
  }

  private fun updateSectionSyncState(
    section: SyncSection,
    transform: (SectionSyncState) -> SectionSyncState
  ) {
    _sectionSyncStates.update { current ->
      current + (section to transform(current[section] ?: SectionSyncState()))
    }
  }

  suspend fun refreshSection(section: SyncSection): Boolean = withSessionContext {
    refreshSectionForSession(section)
  }

  private suspend fun refreshSectionForSession(section: SyncSection): Boolean {
    val snapshot = checkNotNull(currentCoroutineContext()[SessionContext]).snapshot
    val key = snapshot.apiKey
    val profileId = snapshot.profileId
    if (key.isBlank() || profileId.isBlank()) return false
    val token = requestSequence.incrementAndGet()
    commitCurrentSession {
      refreshRequests[section] = token
      updateSectionSyncState(section) {
        it.copy(isRefreshing = true, lastAttemptAt = System.currentTimeMillis(), errorMessage = null)
      }
    }
    var success = false
    var cancelled = false
    try {
      success = withContext(RefreshContext(section, token)) {
        when (section) {
          SyncSection.SETUP -> applySetupFromApi(key, profileId)
          SyncSection.SECURITY -> applySecuritySettingsFromApi(key, profileId)
          SyncSection.PRIVACY -> applyPrivacySettingsFromApi(key, profileId)
          SyncSection.PARENTAL -> applyParentalSettingsFromApi(key, profileId)
          SyncSection.DENYLIST -> applyDenylistFromApi(key, profileId)
          SyncSection.ALLOWLIST -> applyAllowlistFromApi(key, profileId)
          SyncSection.SETTINGS -> applyConfigSettingsFromApi(key, profileId)
        }
      }
      return success
    } catch (error: CancellationException) {
      cancelled = true
      throw error
    } catch (error: Exception) {
      if (BuildConfig.DEBUG) Log.e(TAG, "[refreshSection] error", error)
      return false
    } finally {
      synchronized(sessionLock) {
        if (isCurrentSession(snapshot) && refreshRequests[section] == token) {
          refreshRequests.remove(section)
          updateSectionSyncState(section) {
            it.copy(isRefreshing = false,
              lastSuccessAt = if (success) System.currentTimeMillis() else it.lastSuccessAt,
              errorMessage = when {
                success -> null
                cancelled -> it.errorMessage
                else -> AppStrings.get(R.string.refresh_failed)
              })
          }
        }
      }
    }
  }

  private suspend fun mutateSection(
    section: SyncSection,
    operationName: String,
    verify: () -> Boolean,
    action: suspend (apiKey: String, profileId: String) -> Response<NextDnsMutationResponse>
  ): Result<Unit> = withSessionContext {
    val snapshot = checkNotNull(currentCoroutineContext()[SessionContext]).snapshot
    val key = snapshot.apiKey
    val profileId = snapshot.profileId
    if (key.isBlank() || profileId.isBlank()) {
      return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_75ad86c6ba)))
    }
    if (!mutationCoordinator.tryEnter(section)) {
      return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_f0f54e91df)))
    }
    val affectsNotifications = section in setOf(SyncSection.SECURITY, SyncSection.PRIVACY,
      SyncSection.PARENTAL, SyncSection.DENYLIST, SyncSection.ALLOWLIST, SyncSection.SETTINGS)
    var suppression = false
    var verified = false
    try {
      commitCurrentSession { updateSectionSyncState(section) { it.copy(isSaving = true, errorMessage = null) } }
      if (affectsNotifications) {
        suppression = safeApiCall("beginLocalConfigMutation") {
          NotificationWorkScheduler.beginLocalConfigMutation(NextDnsApp.instance, profileId)
        } ?: false
      }
      val response = safeApiCall(operationName) { action(key, profileId) }
      val message = when {
        response == null -> AppStrings.get(R.string.ui_0e6df5dfd7)
        !response.isMutationAccepted() -> response.body()?.errors?.firstOrNull()?.detail
          ?: AppStrings.get(R.string.operation_http, response.code())
        // Read back the captured target, never recapture a different active profile.
        !refreshSectionForSession(section) || !commitCurrentSession { verify() } -> AppStrings.get(R.string.ui_4940b95279)
        else -> null
      }
      if (message != null) {
        commitCurrentSession { updateSectionSyncState(section) { it.copy(errorMessage = message) } }
        return@withSessionContext Result.failure(IllegalStateException(message))
      }
      ensureOperationActive()
      verified = true
      if (affectsNotifications) safeApiCall("refreshConfigBaseline") {
        NotificationWorkScheduler.refreshConfigBaselineIfEnabled(NextDnsApp.instance)
      }
      ensureOperationActive()
      Result.success(Unit)
    } finally {
      val current = synchronized(sessionLock) { isCurrentSession(snapshot) }
      if (suppression && !verified && current) {
        withContext(NonCancellable) {
          runCatching { NotificationWorkScheduler.abortLocalConfigMutation(NextDnsApp.instance, profileId) }
        }
      }
      synchronized(sessionLock) {
        if (isCurrentSession(snapshot)) updateSectionSyncState(section) { it.copy(isSaving = false) }
      }
      mutationCoordinator.exit(section)
    }
  }

  private fun Response<NextDnsMutationResponse>.isMutationAccepted(): Boolean =
    isSuccessful && body()?.errors.isNullOrEmpty()

  // Live NextDNS Public Catalogs & Account Metadata
  private val _availableBlocklistsCatalog = MutableStateFlow<List<BlocklistEntry>>(emptyList())
  val availableBlocklistsCatalog = _availableBlocklistsCatalog.asStateFlow()

  private val _availableNativesCatalog = MutableStateFlow<List<NativeTrackingDto>>(emptyList())
  val availableNativesCatalog = _availableNativesCatalog.asStateFlow()

  private val _availableParentalServicesCatalog = MutableStateFlow<List<ParentalServiceCatalogDto>>(emptyList())
  val availableParentalServicesCatalog = _availableParentalServicesCatalog.asStateFlow()

  private val _availableParentalCategoriesCatalog = MutableStateFlow<List<ParentalCategoryDto>>(emptyList())
  val availableParentalCategoriesCatalog = _availableParentalCategoriesCatalog.asStateFlow()

  private val _availableTldsCatalog = MutableStateFlow<List<SecurityTldCatalogDto>>(emptyList())
  val availableTldsCatalog = _availableTldsCatalog.asStateFlow()

  private val _profileSetup = MutableStateFlow(SetupDto())
  val profileSetup = _profileSetup.asStateFlow()

  init {
    resumeAfterTermsAccepted()
  }

  /**
   * Never start catalogs, diagnostics, credential restoration or API calls
   * before an explicit current Terms revision is durably accepted.
   */
  fun resumeAfterTermsAccepted() {
    if (!backgroundWorkEnabled) return
    if (!legalAcceptanceStore.isAccepted()) return
    if (!startupInitialized.compareAndSet(false, true)) return

    repoScope.launch {
      loadAllLiveCatalogs()
      runDiagnosticTest()
      val savedKey = preferences.apiKey
      val savedPid = preferences.activeProfileId
      if (savedKey.isNotBlank()) {
        loginWithApiKey(savedKey, restoreProfileId = savedPid)
      } else if (savedPid.isNotBlank()) {
        loadLocalProfileData(savedPid)
      }
    }
  }

  // =========================================================================
  // Safe API Helper (Eliminates try/catch duplication and bumpy road)
  // =========================================================================

  private suspend fun <T> safeApiCall(operationName: String, block: suspend () -> T): T? {
    return try {
      ensureOperationActive()
      val result = block()
      ensureOperationActive()
      result
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (BuildConfig.DEBUG) Log.e(TAG, "[$operationName] error: ${e.message}", e)
      null
    }
  }

  private suspend fun <T> fetchAllPages(
    operationName: String,
    request: suspend (cursor: String?) -> Response<NextDnsApiResponse<List<T>>>
  ): List<T>? {
    val items = mutableListOf<T>()
    val seenCursors = mutableSetOf<String>()
    var cursor: String? = null

    do {
      val response = safeApiCall(operationName) {
        request(cursor)
      } ?: return null

      val body = response.body() ?: return null
      if (!response.isSuccessful || body.hasApiErrors()) return null

      items += body.data.orEmpty()

      val nextCursor = body.meta?.pagination?.cursor
      cursor = if (!nextCursor.isNullOrBlank() && seenCursors.add(nextCursor)) {
        nextCursor
      } else {
        null
      }
    } while (cursor != null)

    return items
  }

  suspend fun loadAllLiveCatalogs() = withContext(Dispatchers.IO) {
    try {
      // 1. Blocklists Catalog
      val blocklistDtos = fetchAllPages("availableBlocklists") { cursor ->
        apiService.getAvailableBlocklists(apiKey = null, cursor = cursor)
      } ?: NextDnsNetworkClient.fetchAvailableBlocklistsDirect()
      if (blocklistDtos != null && blocklistDtos.isNotEmpty()) {
        _availableBlocklistsCatalog.value = blocklistDtos.map { dto ->
          val cleanId = dto.id.lowercase().trim()
          val isRecommended = cleanId == "nextdns-recommended"
          BlocklistEntry(
            id = dto.id,
            name = dto.name?.takeIf { it.isNotBlank() } ?: (if (isRecommended) AppStrings.get(R.string.ui_13e3677beb) else dto.id),
            description = dto.description ?: (if (isRecommended) AppStrings.get(R.string.ui_08080164e9) else ""),
            entriesCount = dto.entries ?: 0L,
            active = false,
            website = dto.website ?: (if (isRecommended) "https://nextdns.io" else ""),
            category = determineBlocklistCategory(cleanId, dto.name ?: "", dto.description ?: ""),
            updatedTime = formatIsoDateWithRelative(dto.updatedOn)
          )
        }
      }

      // 2. Natives Catalog
      val nativesDtos = fetchAllPages("availableNatives") { cursor ->
        apiService.getAvailableNatives(cursor = cursor)
      } ?: NextDnsNetworkClient.fetchAvailableNativesDirect()
      if (nativesDtos != null && nativesDtos.isNotEmpty()) {
        _availableNativesCatalog.value = nativesDtos
      }

      // 3. Parental Services Catalog
      val parentServices = fetchAllPages("availableParentalServices") { cursor ->
        apiService.getAvailableParentalServices(cursor = cursor)
      } ?: NextDnsNetworkClient.fetchAvailableParentalServicesDirect()
      if (parentServices != null && parentServices.isNotEmpty()) {
        _availableParentalServicesCatalog.value = parentServices
      }

      // 4. Parental Categories Catalog
      val parentCats = fetchAllPages("availableParentalCategories") { cursor ->
        apiService.getAvailableParentalCategories(cursor = cursor)
      } ?: NextDnsNetworkClient.fetchAvailableParentalCategoriesDirect()
      if (parentCats != null && parentCats.isNotEmpty()) {
        _availableParentalCategoriesCatalog.value = parentCats
      }

      // 5. TLDs Catalog
      val tlds = fetchAllPages("availableTlds") { cursor ->
        apiService.getAvailableTlds(cursor = cursor)
      } ?: NextDnsNetworkClient.fetchAvailableTldsDirect()
      if (tlds != null && tlds.isNotEmpty()) {
        _availableTldsCatalog.value = tlds
      }
    } catch (e: Exception) {
      if (BuildConfig.DEBUG) Log.e(TAG, "Error loading live catalogs: ${e.message}", e)
    }
  }

  fun loadLocalProfileData(profileId: String) {
    synchronized(sessionLock) {
      if (profileId != _activeProfileId.value) return
      _securitySettings.value = preferences.getSecuritySettings(profileId) ?: SecuritySettings()
      _privacySettings.value = preferences.getPrivacySettings(profileId) ?: PrivacySettings()
      _parentalControlSettings.value = preferences.getParentalControlSettings(profileId) ?: ParentalControlSettings()
      _denylist.value = preferences.getDenylist(profileId) ?: emptyList()
      _allowlist.value = preferences.getAllowlist(profileId) ?: emptyList()
      _configSettings.value = preferences.getConfigSettings(profileId) ?: ConfigSettings()
      _logs.value = emptyList()
      nextLogsCursor = null
      logsRefreshRequest = null
      logsSnapshotGeneration++
      _logsPaginationState.value = LogsPaginationState()
    }
  }

  // =========================================================================
  // Diagnostic Tests
  // =========================================================================

  suspend fun runDiagnosticTest(targetProfileId: String? = null): DiagnosticTestResult = withSessionContext {
    val snapshot = checkNotNull(currentCoroutineContext()[SessionContext]).snapshot
    val token = requestSequence.incrementAndGet()
    commitCurrentSession {
      diagnosticRequest = token
      _testResult.value = _testResult.value.copy(isTesting = true)
    }
    val started = System.currentTimeMillis()
    try {
      val profileId = targetProfileId ?: snapshot.profileId.takeIf { it.isNotBlank() }
      var body = diagnosticProbe(profileId)
      ensureOperationActive()
      if (body == null) body = safeApiCall("runDiagnosticTest") {
        val response = diagnosticService.testConnection()
        if (response.isSuccessful) response.body() else null
      }
      val measurement = body
      commitCurrentSession {
        if (diagnosticRequest != token) throw CancellationException("Diagnostic superseded")
        val previous = _testResult.value
        val result = if (measurement != null) {
          DiagnosticTestResult(status = measurement.status.orEmpty().ifBlank { "unconfigured" }.lowercase().trim(),
            protocol = measurement.protocol.orEmpty(), profileId = measurement.profile.orEmpty(),
            clientIp = measurement.client?.takeIf { it.isNotBlank() } ?: measurement.srcIP.orEmpty(),
            resolver = measurement.resolver, serverPoP = measurement.server.orEmpty(), anycast = measurement.anycast,
            latencyMs = (System.currentTimeMillis() - started).toInt().coerceAtLeast(1),
            isEncrypted = measurement.protocol?.uppercase() in listOf("DOH", "DOT", "DOQ"),
            lastTestedTime = System.currentTimeMillis())
        } else {
          // Keep the last successful measurement time; failure is never a fresh success.
          previous.copy(status = "error", isTesting = false,
            errorMessage = AppStrings.get(R.string.ui_6da43a7f63))
        }
        _testResult.value = result
        result
      }
    } finally {
      synchronized(sessionLock) {
        if (isCurrentSession(snapshot) && diagnosticRequest == token) {
          diagnosticRequest = null
          _testResult.value = _testResult.value.copy(isTesting = false)
        }
      }
    }
  }

  suspend fun linkCurrentIp(profileId: String): Boolean = withSessionContext(expectedProfile = profileId) {
    val ok = NextDnsNetworkClient.linkIpAddress(profileId)
    runDiagnosticTest()
    ok
  }

  // =========================================================================
  // Authentication & Profile Fetching
  // =========================================================================

  private suspend fun fetchAllProfilesFromApi(key: String): Result<List<ProfileDto>> {
    val profiles = mutableListOf<ProfileDto>()
    val seenCursors = mutableSetOf<String>()
    var cursor: String? = null

    do {
      val response = safeApiCall("getProfiles") {
        apiService.getProfiles(key, cursor)
      } ?: return Result.failure(
        ProfilesFetchException(-1, AppStrings.get(R.string.ui_8520ff5f87))
      )

      val body = response.body()
        ?: return Result.failure(
          ProfilesFetchException(response.code(), AppStrings.get(R.string.ui_7589647af2))
        )
      if (!response.isSuccessful || body.hasApiErrors()) {
        val detail = body.errors?.firstOrNull()?.detail
          ?: AppStrings.get(R.string.ui_f64dc7c6b4)
        return Result.failure(ProfilesFetchException(response.code(), detail))
      }

      profiles += body.data.orEmpty()
      val nextCursor = body.meta?.pagination?.cursor
      cursor = if (!nextCursor.isNullOrBlank() && seenCursors.add(nextCursor)) nextCursor else null
    } while (cursor != null)

    return Result.success(profiles.distinctBy { it.id })
  }

  suspend fun loginWithApiKey(key: String, restoreProfileId: String? = null): Result<Int> {
    if (!legalAcceptanceStore.isAccepted()) {
      return Result.failure(IllegalStateException(AppStrings.get(R.string.ui_cf61b6f813)))
    }
    val attempt = synchronized(sessionLock) {
      advanceSession()
      resetProfileScopedRuntimeState()
      _apiStatus.value = ApiConnectionStatus.Connecting
      captureSession()
    }
    return withSessionContext(snapshot = attempt) {
      val result = fetchAllProfilesFromApi(key)
      if (result.isFailure) {
        val cause = result.exceptionOrNull()
        val message = when ((cause as? ProfilesFetchException)?.httpCode) {
          401 -> AppStrings.get(R.string.invalid_api_key)
          403 -> AppStrings.get(R.string.ui_57380f7ad7)
          else -> cause?.message ?: AppStrings.get(R.string.ui_62b3e50501)
        }
        commitCurrentSession { _apiStatus.value = ApiConnectionStatus.Error(message) }
        return@withSessionContext Result.failure(Exception(message))
      }
      val mapped = result.getOrThrow().map {
        NextDnsProfile(it.id, it.name, it.fingerprint.orEmpty())
      }
      if (mapped.isEmpty()) {
        val message = AppStrings.get(R.string.ui_564ed4ba2d)
        commitCurrentSession { _apiStatus.value = ApiConnectionStatus.Error(message) }
        return@withSessionContext Result.failure(Exception(message))
      }
      val target = restoreProfileId?.takeIf { restored -> mapped.any { it.id == restored } } ?: mapped.first().id
      val owner = currentCoroutineContext()[SessionContext]?.owner
      val storage = commitCurrentSession {
        runCatching {
          if (_apiKey.value != key) check(preferences.clear()) { "Account cache could not be cleared" }
          preferences.apiKey = key
          advanceSession(owner)
          resetProfileScopedRuntimeState()
          _apiKey.value = key
          _profiles.value = mapped
          preferences.saveProfiles(mapped)
          _activeProfileId.value = target
          preferences.activeProfileId = target
          loadLocalProfileData(target)
          _apiStatus.value = ApiConnectionStatus.Connected(mapped.size)
        }
      }
      if (storage.isFailure) {
        synchronized(sessionLock) {
          // The storage failure precedes a successful transition.
          if (isCurrentSession(attempt)) {
            _apiKey.value = ""
            _apiStatus.value = ApiConnectionStatus.Error(AppStrings.get(R.string.ui_f8222e6ff1))
          }
        }
        return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_f8222e6ff1)))
      }
      loadActiveProfileDataFromApi(key, target)
      withSessionContext(expectedKey = key, expectedProfile = target) {
        safeApiCall("reconcileNotifications") { NotificationWorkScheduler.reconcile(NextDnsApp.instance) }
        ensureOperationActive()
      }
      Result.success(mapped.size)
    }
  }

  suspend fun refreshProfilesFromApi(): Boolean = withSessionContext {
    refreshProfilesForSession()
  }

  private suspend fun refreshProfilesForSession(
    verify: (List<NextDnsProfile>) -> Boolean = { true }
  ): Boolean {
    val key = checkNotNull(currentCoroutineContext()[SessionContext]).snapshot.apiKey
    if (key.isBlank()) return false
    val result = fetchAllProfilesFromApi(key)
    if (result.isFailure) return false
    val remote = result.getOrThrow().map { NextDnsProfile(it.id, it.name, it.fingerprint.orEmpty()) }
    // Verify the mutation before a legitimate selected-profile transition advances the session.
    if (!verify(remote)) return false
    val owner = currentCoroutineContext()[SessionContext]?.owner
    val replacement = commitCurrentSession {
      _profiles.value = remote
      preferences.saveProfiles(remote)
      _apiStatus.value = ApiConnectionStatus.Connected(remote.size)
      if (remote.none { it.id == _activeProfileId.value }) {
        advanceSession(owner)
        resetProfileScopedRuntimeState()
        val next = remote.firstOrNull()?.id.orEmpty()
        _activeProfileId.value = next
        preferences.activeProfileId = next
        loadLocalProfileData(next)
        next
      } else null
    }
    if (backgroundWorkEnabled && !replacement.isNullOrBlank()) loadActiveProfileDataFromApi(key, replacement)
    return true
  }

  suspend fun loadActiveProfileDataFromApi(
    key: String = _apiKey.value,
    profileId: String = _activeProfileId.value
  ): Map<SyncSection, Boolean> = withSessionContext(expectedKey = key, expectedProfile = profileId) {
    if (key.isBlank() || profileId.isBlank()) return@withSessionContext emptyMap()
    val snapshot = checkNotNull(currentCoroutineContext()[SessionContext]).snapshot
    val token = requestSequence.incrementAndGet()
    commitCurrentSession { fullSyncRequest = token; _isSyncing.value = true }
    try {
      val sectionResults = linkedMapOf<SyncSection, Boolean>()
      SyncSection.values().forEach { section ->
        sectionResults[section] = refreshSectionForSession(section)
      }
      applyLogsFromApi(key, profileId)
      applyDevicesAnalyticsFromApi(key, profileId)
      fetchAnalytics(key, profileId, null, null)
      sectionResults
    } finally {
      synchronized(sessionLock) {
        if (isCurrentSession(snapshot) && fullSyncRequest == token) {
          fullSyncRequest = null
          _isSyncing.value = false
        }
      }
    }
  }

  private suspend fun applySetupFromApi(key: String, profileId: String): Boolean {
    val setupResp = safeApiCall("getProfileSetup") {
      apiService.getProfileSetup(key, profileId)
    } ?: return false
    if (!setupResp.isSuccessful) return false
    val d = setupResp.body() ?: return false
    return commitCurrentSession {
      _profileSetup.value = d
      true
    }
  }

  // =========================================================================
  // Settings Loading Sub-Routines (Guard Clause & Single Responsibility)
  // =========================================================================

  private suspend fun applySecuritySettingsFromApi(key: String, profileId: String): Boolean {
    val secResp = safeApiCall("applySecuritySettings") {
      apiService.getSecurity(key, profileId)
    } ?: return false
    val body = secResp.body() ?: return false
    if (!secResp.isSuccessful || body.hasApiErrors()) return false
    val d = body.data ?: return false
    return commitCurrentSession {
      val previous = _securitySettings.value

      val tldList = d.tlds?.map { it.id } ?: previous.blockedTlds
      val updated = SecuritySettings(
        threatIntelligenceFeeds = d.threatIntelligenceFeeds ?: previous.threatIntelligenceFeeds,
        aiThreatDetection = d.aiThreatDetection ?: previous.aiThreatDetection,
        googleSafeBrowsing = d.googleSafeBrowsing ?: previous.googleSafeBrowsing,
        cryptojacking = d.cryptojacking ?: previous.cryptojacking,
        dnsRebinding = d.dnsRebinding ?: previous.dnsRebinding,
        idnHomographs = d.idnHomographs ?: previous.idnHomographs,
        typosquatting = d.typosquatting ?: previous.typosquatting,
        dga = d.dga ?: previous.dga,
        nrd = d.nrd ?: previous.nrd,
        ddns = d.ddns ?: previous.ddns,
        parkedDomains = d.parking ?: previous.parkedDomains,
        csam = d.csam ?: previous.csam,
        blockedTlds = tldList
      )
      _securitySettings.value = updated
      preferences.saveSecuritySettings(profileId, updated)
      true
    }
  }

  private suspend fun applyPrivacySettingsFromApi(key: String, profileId: String): Boolean {
    var activeBlocklistDtos: List<BlocklistDto>? = null
    var activeNativeDtos: List<NativeTrackingDto>? = null
    var disguisedTrackersVal: Boolean? = null
    var allowAffiliatesVal: Boolean? = null

    activeBlocklistDtos = fetchAllPages("getProfileBlocklists") { cursor ->
      apiService.getProfileBlocklists(
        key, profileId, cursor = cursor
      )
    }

    activeNativeDtos = fetchAllPages("getProfileNatives") { cursor ->
      apiService.getProfileNatives(
        key, profileId, cursor = cursor
      )
    }

    val privResp = safeApiCall("getPrivacy") {
      apiService.getPrivacy(key, profileId)
    }
    val privBody = privResp?.body()
    if (privResp?.isSuccessful == true && privBody.isSemanticallySuccessful()) {
      val p = privBody?.data
      if (p != null) {
        if (activeBlocklistDtos == null) activeBlocklistDtos = p.blocklists
        if (activeNativeDtos == null) activeNativeDtos = p.natives
        disguisedTrackersVal = p.disguisedTrackers
        allowAffiliatesVal = p.allowAffiliateLinks
      }
    }

    var availableDtos = fetchAllPages("getAvailableBlocklists") { cursor ->
      apiService.getAvailableBlocklists(
        apiKey = key,
        cursor = cursor
      )
    }
    if (availableDtos.isNullOrEmpty()) {
      availableDtos = _availableBlocklistsCatalog.value.takeIf { it.isNotEmpty() }?.map {
        BlocklistDto(
          id = it.id,
          name = it.name,
          description = it.description,
          entries = it.entriesCount,
          website = it.website,
          updatedOn = null
        )
      } ?: NextDnsNetworkClient.fetchAvailableBlocklistsDirect()
    }
    return commitCurrentSession {
      val safeAvailDtos = availableDtos ?: emptyList()

      val activeMap = activeBlocklistDtos?.associateBy { it.id.lowercase().trim() } ?: emptyMap()

      val updatedBlocklists = if (safeAvailDtos.isNotEmpty()) {
        val list = safeAvailDtos.map { dto ->
          val cleanId = dto.id.lowercase().trim()
          val isActive = activeMap.containsKey(cleanId)
          val isRecommended = cleanId == "nextdns-recommended"

          val displayName = when {
            !dto.name.isNullOrBlank() -> dto.name
            isRecommended -> AppStrings.get(R.string.ui_13e3677beb)
            else -> dto.id
          }

          val displayDesc = when {
            !dto.description.isNullOrBlank() -> dto.description
            isRecommended -> AppStrings.get(R.string.recommended_list_description)
            else -> ""
          }

          val displayWebsite = when {
            !dto.website.isNullOrBlank() -> dto.website
            isRecommended -> "https://nextdns.io"
            else -> ""
          }

          val formattedUpdated = formatIsoDateWithRelative(dto.updatedOn)
          val category = determineBlocklistCategory(cleanId, displayName, displayDesc)

          BlocklistEntry(
            id = dto.id,
            name = displayName,
            description = displayDesc,
            entriesCount = dto.entries ?: 0L,
            active = isActive,
            website = displayWebsite,
            category = category,
            updatedTime = formattedUpdated
          )
        }.toMutableList()

        // Also append any active blocklist in the profile that is not in the public list
        val existingIds = list.map { it.id.lowercase().trim() }.toSet()
        activeBlocklistDtos?.forEach { activeDto ->
          val cleanId = activeDto.id.lowercase().trim()
          if (!existingIds.contains(cleanId)) {
            list.add(
              BlocklistEntry(
                id = activeDto.id,
                name = activeDto.name ?: activeDto.id,
                description = activeDto.description ?: AppStrings.get(R.string.ui_9809519988),
                entriesCount = activeDto.entries ?: 0L,
                active = true,
                website = activeDto.website ?: "",
                category = AppStrings.get(R.string.ui_7bd8b8264c),
                updatedTime = formatIsoDateWithRelative(activeDto.updatedOn)
              )
            )
          }
        }
        list
      } else {
        val baseCatalog = _privacySettings.value.blocklists.ifEmpty { _availableBlocklistsCatalog.value }
        baseCatalog.map { catItem ->
          val cleanId = catItem.id.lowercase().trim()
          val remote = activeMap[cleanId]
          if (remote != null) {
            catItem.copy(
              id = remote.id,
              active = true,
              name = remote.name?.takeIf { it.isNotBlank() } ?: catItem.name,
              entriesCount = remote.entries ?: catItem.entriesCount,
              updatedTime = formatIsoDateWithRelative(remote.updatedOn).ifBlank { catItem.updatedTime }
            )
          } else {
            catItem.copy(active = false)
          }
        }
      }

      val baseNatives = _privacySettings.value.nativeTracking.ifEmpty {
        _availableNativesCatalog.value.map { NativeTrackingEntry(id = it.id, name = it.id.replaceFirstChar { c -> c.uppercase() }, active = false) }
      }
      val updatedNatives = if (activeNativeDtos != null) {
        val activeNatIds = activeNativeDtos.map { it.id.lowercase().trim() }.toSet()
        baseNatives.map { nat ->
          nat.copy(active = activeNatIds.contains(nat.id.lowercase().trim()))
        }
      } else {
        baseNatives
      }

      val updated = PrivacySettings(
        blocklists = updatedBlocklists,
        nativeTracking = updatedNatives,
        disguisedTrackers = disguisedTrackersVal ?: _privacySettings.value.disguisedTrackers,
        allowAffiliates = allowAffiliatesVal ?: _privacySettings.value.allowAffiliates
      )
      _privacySettings.value = updated
      preferences.savePrivacySettings(profileId, updated)
      return@commitCurrentSession privResp?.isSuccessful == true &&
        privBody.isSemanticallySuccessful() &&
        privBody?.data != null
    }
  }

  private suspend fun applyParentalSettingsFromApi(key: String, profileId: String): Boolean {
    val parentResp = safeApiCall("getParentalControl") {
      apiService.getParentalControl(key, profileId)
    } ?: return false
    val body = parentResp.body() ?: return false
    if (!parentResp.isSuccessful || body.hasApiErrors()) return false
    val pc = body.data ?: return false

    var servCatalog = _availableParentalServicesCatalog.value
    if (servCatalog.isEmpty()) {
      val fetched = NextDnsNetworkClient.fetchAvailableParentalServicesDirect()
      if (fetched != null && fetched.isNotEmpty()) {
        servCatalog = fetched
        commitCurrentSession { _availableParentalServicesCatalog.value = fetched }
      }
    }

    var catCatalog = _availableParentalCategoriesCatalog.value
    if (catCatalog.isEmpty()) {
      val fetched = NextDnsNetworkClient.fetchAvailableParentalCategoriesDirect()
      if (fetched != null && fetched.isNotEmpty()) {
        catCatalog = fetched
        commitCurrentSession { _availableParentalCategoriesCatalog.value = fetched }
      }
    }

    return commitCurrentSession {
      val activeServiceMap = pc.services?.associate { it.id.lowercase().trim() to (it.active != false) } ?: emptyMap()
      val activeCatMap = pc.categories?.associate { it.id.lowercase().trim() to (it.active != false) } ?: emptyMap()

      val updatedServices = if (servCatalog.isNotEmpty()) {
        servCatalog.map { s ->
          BlockedServiceEntry(
            id = s.id,
            name = s.id.replaceFirstChar { it.uppercase() },
            website = s.website,
            active = activeServiceMap[s.id.lowercase().trim()] == true
          )
        }
      } else {
        _parentalControlSettings.value.services.map { s ->
          val clean = s.id.lowercase().trim()
          if (activeServiceMap.containsKey(clean)) s.copy(active = activeServiceMap[clean] == true) else s
        }
      }

      val updatedCats = if (catCatalog.isNotEmpty()) {
        catCatalog.map { c ->
          BlockedCategoryEntry(
            id = c.id,
            name = c.id.replaceFirstChar { it.uppercase() },
            description = "",
            active = activeCatMap[c.id.lowercase().trim()] == true
          )
        }
      } else {
        _parentalControlSettings.value.categories.map { c ->
          val clean = c.id.lowercase().trim()
          if (activeCatMap.containsKey(clean)) c.copy(active = activeCatMap[clean] == true) else c
        }
      }

      val updated = ParentalControlSettings(
        services = updatedServices,
        categories = updatedCats,
        safeSearch = pc.safeSearch ?: _parentalControlSettings.value.safeSearch,
        youtubeRestrictedMode = pc.youtubeRestrictedMode ?: _parentalControlSettings.value.youtubeRestrictedMode,
        blockBypass = pc.blockBypass ?: _parentalControlSettings.value.blockBypass
      )
      _parentalControlSettings.value = updated
      preferences.saveParentalControlSettings(profileId, updated)
      true
    }
  }

  private suspend fun applyDenylistFromApi(key: String, profileId: String): Boolean {
    val items = fetchAllPages("getDenylist") { cursor ->
      apiService.getDenylist(
        key, profileId, cursor = cursor
      )
    } ?: return false

    return commitCurrentSession {
      val list = items.map { AllowDenyItem(id = it.id, domain = it.id, active = it.active != false) }
      _denylist.value = list
      preferences.saveDenylist(profileId, list)
      true
    }
  }

  private suspend fun applyAllowlistFromApi(key: String, profileId: String): Boolean {
    val items = fetchAllPages("getAllowlist") { cursor ->
      apiService.getAllowlist(
        key, profileId, cursor = cursor
      )
    } ?: return false

    return commitCurrentSession {
      val list = items.map { AllowDenyItem(id = it.id, domain = it.id, active = it.active != false) }
      _allowlist.value = list
      preferences.saveAllowlist(profileId, list)
      true
    }
  }

  private suspend fun applyConfigSettingsFromApi(key: String, profileId: String): Boolean {
    val cfgResp = safeApiCall("getSettings") {
      apiService.getSettings(key, profileId)
    } ?: return false
    val body = cfgResp.body() ?: return false
    if (!cfgResp.isSuccessful || body.hasApiErrors()) return false
    val s = body.data ?: return false

    return commitCurrentSession {
      val locName = when (s.logs?.location) {
        "ch" -> "İsviçre (CH)"
        "eu" -> "Avrupa Birliği (AB)"
        "us" -> "Amerika Birleşik Devletleri (ABD)"
        else -> _configSettings.value.logStorageLocation
      }
      val retName = LogRetentionCodec.toLabel(s.logs?.retention) ?: "Bilinmiyor"

      val updated = _configSettings.value.copy(
        logsEnabled = s.logs?.enabled ?: _configSettings.value.logsEnabled,
        logClientIps = if (s.logs?.drop?.ip != null) !s.logs.drop.ip else _configSettings.value.logClientIps,
        logDomains = if (s.logs?.drop?.domain != null) !s.logs.drop.domain else _configSettings.value.logDomains,
        logRetention = retName,
        logStorageLocation = locName,
        blockPage = s.blockPage?.enabled ?: _configSettings.value.blockPage,
        ednsClientSubnet = s.performance?.ecs ?: _configSettings.value.ednsClientSubnet,
        cacheBoost = s.performance?.cacheBoost ?: _configSettings.value.cacheBoost,
        cnameFlattening = s.performance?.cnameFlattening ?: _configSettings.value.cnameFlattening,
        web3 = s.web3 ?: _configSettings.value.web3
      )
      _configSettings.value = updated
      preferences.saveConfigSettings(profileId, updated)
      true
    }
  }

  private suspend fun applyLogsFromApi(key: String, profileId: String) {
    val deviceFilter = _logsDeviceFilter.value
    val statusFilter = _logsStatusFilter.value
    val searchFilter = _logsSearchFilter.value
    val token = requestSequence.incrementAndGet()
    val generation = commitCurrentSession {
      logsRefreshRequest = token
      ++logsSnapshotGeneration
    }
    val logsResp = safeApiCall("getLogs") {
      apiService.getLogs(key, profileId, limit = 100, search = searchFilter, device = deviceFilter, status = statusFilter, raw = 1)
    }
    val body = logsResp?.body()
    if (logsResp?.isSuccessful != true || body == null || body.hasApiErrors()) {
      commitCurrentSession {
        if (logsRefreshRequest == token) logsRefreshRequest = null
      }
      return
    }

    commitCurrentSession {
      if (logsRefreshRequest != token || logsSnapshotGeneration != generation ||
        _logsDeviceFilter.value != deviceFilter || _logsStatusFilter.value != statusFilter ||
        _logsSearchFilter.value != searchFilter) return@commitCurrentSession
      logsStreamSeedId = body.meta?.stream?.id
      val fetchedLogs = parseLogsResponse(body.data.orEmpty())
      val retained = fetchedLogs.take(MAX_RETAINED_LOG_ENTRIES)
      nextLogsCursor = body.meta?.pagination?.cursor
        ?.takeIf { retained.size < MAX_RETAINED_LOG_ENTRIES }
      _logs.value = retained
      _logsPaginationState.value = LogsPaginationState(
        hasMore = nextLogsCursor != null,
        retentionLimitReached = fetchedLogs.size > retained.size ||
          (retained.size == MAX_RETAINED_LOG_ENTRIES && body.meta?.pagination?.cursor != null)
      )
      logsRefreshRequest = null
    }
  }

  private suspend fun applyDevicesAnalyticsFromApi(key: String, profileId: String) {
    val devResp = safeApiCall("getAnalyticsDevices") { apiService.getAnalyticsDevices(key, profileId) } ?: return
    val dtoList = devResp.body()?.data ?: return
    if (!devResp.isSuccessful) return

    commitCurrentSession {
      val devItems = dtoList.map {
        val id = it.id ?: ""
        val name = DeviceLabelResolver.resolve(
          it.name, it.model, id, AppStrings.get(R.string.device_unidentified)
        )
        rememberLogDevice(id, name, it.model)
        DeviceMetric(
          id = id,
          name = name,
          queries = it.queries ?: 0L,
          clientIp = it.localIp.orEmpty(),
          model = it.model.orEmpty()
        )
      }
      if (devItems.isNotEmpty()) {
        updateKnownDevices(devItems.map { it.name })
        _analytics.value = _analytics.value.copy(topDevices = devItems)
      }
    }
  }

  // =========================================================================
  // Logs Parsing
  // =========================================================================

  private fun parseLogsResponse(dtoList: List<DnsLogDto>): List<DnsLogEntry> {
    val parsedLogs = dtoList.map { l ->
      val devId = l.device?.id
      val candidateName = l.device?.name?.takeIf { it.isNotBlank() }
        ?: l.deviceName?.takeIf { it.isNotBlank() }
        ?: l.deviceNameSnake?.takeIf { it.isNotBlank() }
        ?: (if (!devId.isNullOrBlank()) _knownDeviceIdToName[devId] else null)
      val devModel = l.device?.model
      val devName = DeviceLabelResolver.resolve(
        candidateName,
        devModel,
        devId,
        AppStrings.get(R.string.device_unidentified)
      )
      val status = normalizedLogStatus(l.status)

      rememberLogDevice(devId, devName, devModel)

      val reasonObj = l.reasons?.firstOrNull()
      val blockReason = formatBlockReason(reasonObj?.id, reasonObj?.name)

      DnsLogEntry(
        id = l.id?.takeIf { it.isNotBlank() } ?: stableLogId(
          l.timestamp?.toString().orEmpty(), l.domain.orEmpty(), devId.orEmpty(), l.status.orEmpty(),
          l.protocol.orEmpty(), l.clientIp ?: l.clientIpSnake.orEmpty(), l.responseTime ?: l.responseTimeSnake
        ),
        timestamp = l.timestamp?.toString() ?: "",
        domain = l.domain?.takeIf { it.isNotBlank() } ?: l.root?.takeIf { it.isNotBlank() } ?: l.rootDomain?.takeIf { it.isNotBlank() } ?: "",
        rootDomain = l.root?.takeIf { it.isNotBlank() } ?: l.rootDomain?.takeIf { it.isNotBlank() } ?: l.domain.orEmpty(),
        deviceId = devId,
        tracker = l.tracker,
        encrypted = l.encrypted,
        client = l.client,
        clientIp = l.clientIp ?: l.clientIpSnake,
        deviceName = devName,
        blocked = status == "blocked",
        status = status,
        blockReason = if (status == "blocked") blockReason else null,
        protocol = l.protocol ?: "",
        dnssec = l.dnssec,
        responseTimeMs = l.responseTime ?: l.responseTimeSnake,
        deviceModel = devModel
      )
    }
    updateKnownDevices(parsedLogs.mapNotNull { it.deviceName })
    return parsedLogs.distinctBy { it.id }
  }

  private fun normalizedLogStatus(value: String?): String = when (value?.trim()?.lowercase(Locale.ROOT)) {
    "default", "error", "blocked", "allowed" -> value.trim().lowercase(Locale.ROOT)
    else -> "unknown"
  }

  private fun stableLogId(
    timestamp: String,
    domain: String,
    deviceId: String,
    status: String,
    protocol: String,
    clientIp: String,
    responseTimeMs: Int?
  ): String {
    val identity = listOf(timestamp, domain, deviceId, status, protocol, clientIp, responseTimeMs?.toString().orEmpty())
      .joinToString("\u001f")
    return UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString()
  }

  // =========================================================================
  // Profile Management
  // =========================================================================

  /** Guest choice supersedes account work without deleting the user's saved credentials. */
  fun suspendForGuest() {
    NotificationWorkScheduler.cancel(NextDnsApp.instance)
    repoScope.coroutineContext.cancelChildren()
    synchronized(sessionLock) {
      advanceSession()
      resetProfileScopedRuntimeState()
      _apiKey.value = ""
      _activeProfileId.value = ""
      _profiles.value = emptyList()
      _allowlist.value = emptyList()
      _denylist.value = emptyList()
      _logs.value = emptyList()
      nextLogsCursor = null
      _logsPaginationState.value = LogsPaginationState()
      _securitySettings.value = SecuritySettings()
      _privacySettings.value = PrivacySettings()
      _parentalControlSettings.value = ParentalControlSettings()
      _configSettings.value = ConfigSettings()
      _apiStatus.value = ApiConnectionStatus.Disconnected
    }
  }

  suspend fun logout(): Result<Unit> {
    // Shut down account-scoped work before clearing its credentials.
    NotificationWorkScheduler.cancel(NextDnsApp.instance)
    repoScope.coroutineContext.cancelChildren()
    val localDataCleared = synchronized(sessionLock) {
      advanceSession()
      resetProfileScopedRuntimeState()
      val cleared = preferences.clear()
      _apiKey.value = ""
      _activeProfileId.value = ""
      _profiles.value = emptyList()
      _denylist.value = emptyList()
      _allowlist.value = emptyList()
      _logs.value = emptyList()
      nextLogsCursor = null
      _logsPaginationState.value = LogsPaginationState()
      _profileSetup.value = SetupDto()
      _securitySettings.value = SecuritySettings()
      _privacySettings.value = PrivacySettings()
      _parentalControlSettings.value = ParentalControlSettings()
      _configSettings.value = ConfigSettings()
      _apiStatus.value = ApiConnectionStatus.Disconnected
      cleared
    }

    if (!localDataCleared) {
      return Result.failure(IllegalStateException(AppStrings.get(R.string.local_clear_failed)))
    }

    return runCatching {
      NotificationPreferences(NextDnsApp.instance).clearAccountState()
    }
  }

  fun setActiveProfile(profileId: String) {
    val snapshot = synchronized(sessionLock) {
      advanceSession()
      resetProfileScopedRuntimeState()
      _activeProfileId.value = profileId
      preferences.activeProfileId = profileId
      loadLocalProfileData(profileId)
      captureSession()
    }
    if (backgroundWorkEnabled && snapshot.apiKey.isNotBlank()) repoScope.launch {
      withSessionContext(snapshot = snapshot) { loadActiveProfileDataFromApi(snapshot.apiKey, profileId) }
    }
    if (backgroundWorkEnabled) repoScope.launch {
      withSessionContext(snapshot = snapshot) { runDiagnosticTest(profileId) }
    }
  }

  suspend fun createProfileRemote(name: String): Result<NextDnsProfile> = withSessionContext {
    val key = _apiKey.value
    if (key.isBlank()) {
      return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_07980aa188)))
    }

    val resp = safeApiCall("createProfile") {
      apiService.createProfile(key, NameRequest(name = name))
    } ?: return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_a5fc13d828)))

    val body = resp.body()
    if (!resp.isSuccessful || body.hasApiErrors()) {
      val detail = body?.errors?.firstOrNull()?.detail ?: AppStrings.get(R.string.ui_43611457f9)
      return@withSessionContext Result.failure(IllegalStateException(detail))
    }

    val newId = body?.data?.id?.takeIf { it.isNotBlank() }
      ?: return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_51e119f7b9)))

    val verified = safeApiCall("getCreatedProfile") {
      apiService.getProfile(key, newId)
    }
    val verifiedBody = verified?.body()
    val profileDto = if (verified?.isSuccessful == true && verifiedBody.isSemanticallySuccessful()) {
      verifiedBody?.data
    } else {
      null
    }

    val verifiedProfile = profileDto?.takeIf { it.id == newId }
      ?: return@withSessionContext Result.failure(
        IllegalStateException(AppStrings.get(R.string.ui_d635045839))
      )

    val created = NextDnsProfile(
      id = newId,
      name = verifiedProfile.name,
      fingerprint = verifiedProfile.fingerprint ?: ""
    )
    val owner = currentCoroutineContext()[SessionContext]?.owner
    commitCurrentSession {
      val updatedList = _profiles.value.filterNot { it.id == newId } + created
      _profiles.value = updatedList
      preferences.saveProfiles(updatedList)
      advanceSession(owner)
      resetProfileScopedRuntimeState()
      _activeProfileId.value = newId
      preferences.activeProfileId = newId
      loadLocalProfileData(newId)
    }
    Result.success(created)
  }

  suspend fun deleteProfileRemote(profileId: String): Result<Unit> = withSessionContext {
    val key = _apiKey.value
    if (key.isBlank()) {
      return@withSessionContext Result.failure(
        IllegalStateException(AppStrings.get(R.string.ui_4028f03245))
      )
    }

    val response = safeApiCall("deleteProfile") {
      apiService.deleteProfile(key, profileId)
    } ?: return@withSessionContext Result.failure(
      IllegalStateException(AppStrings.get(R.string.ui_407ddb5dd2))
    )

    val apiError = response.body()?.errors?.firstOrNull()?.detail
    if (!response.isMutationAccepted()) {
      return@withSessionContext Result.failure(
        IllegalStateException(apiError ?: AppStrings.get(R.string.ui_856fbe8f71))
      )
    }

    val verified = refreshProfilesForSession { remote -> remote.none { it.id == profileId } }
    if (!verified) {
      return@withSessionContext Result.failure(
        IllegalStateException(AppStrings.get(R.string.ui_9cac51a42b))
      )
    }

    Result.success(Unit)
  }

  suspend fun renameProfile(profileId: String, newName: String): Result<Unit> = withSessionContext {
    val key = _apiKey.value
    if (key.isBlank()) {
      return@withSessionContext Result.failure(
        IllegalStateException(AppStrings.get(R.string.ui_de223d8e2f))
      )
    }

    val response = safeApiCall("renameProfile") {
      apiService.renameProfile(
        key, profileId, NameRequest(name = newName)
      )
    } ?: return@withSessionContext Result.failure(
      IllegalStateException(AppStrings.get(R.string.ui_7ae7fc7964))
    )

    val apiError = response.body()?.errors?.firstOrNull()?.detail
    if (!response.isMutationAccepted()) {
      return@withSessionContext Result.failure(
        IllegalStateException(apiError ?: AppStrings.get(R.string.ui_d01c651ccf))
      )
    }

    val verified = refreshProfilesForSession { remote -> remote.any { it.id == profileId && it.name == newName } }
    if (!verified) {
      return@withSessionContext Result.failure(
        IllegalStateException(AppStrings.get(R.string.ui_e144829518))
      )
    }

    Result.success(Unit)
  }

  // =========================================================================
  // Security Updates
  // =========================================================================

  suspend fun setSecurityFlag(flag: SecurityFlag, enabled: Boolean): Result<Unit> {
    val request = when (flag) {
      SecurityFlag.THREAT_INTELLIGENCE_FEEDS -> SecurityUpdateRequest(threatIntelligenceFeeds = enabled)
      SecurityFlag.AI_THREAT_DETECTION -> SecurityUpdateRequest(aiThreatDetection = enabled)
      SecurityFlag.GOOGLE_SAFE_BROWSING -> SecurityUpdateRequest(googleSafeBrowsing = enabled)
      SecurityFlag.CRYPTOJACKING -> SecurityUpdateRequest(cryptojacking = enabled)
      SecurityFlag.DNS_REBINDING -> SecurityUpdateRequest(dnsRebinding = enabled)
      SecurityFlag.IDN_HOMOGRAPHS -> SecurityUpdateRequest(idnHomographs = enabled)
      SecurityFlag.TYPOSQUATTING -> SecurityUpdateRequest(typosquatting = enabled)
      SecurityFlag.DGA -> SecurityUpdateRequest(dga = enabled)
      SecurityFlag.NRD -> SecurityUpdateRequest(nrd = enabled)
      SecurityFlag.DDNS -> SecurityUpdateRequest(ddns = enabled)
      SecurityFlag.PARKING -> SecurityUpdateRequest(parking = enabled)
      SecurityFlag.CSAM -> SecurityUpdateRequest(csam = enabled)
    }

    return mutateSection(
      section = SyncSection.SECURITY,
      operationName = "setSecurityFlag",
      verify = {
        when (flag) {
          SecurityFlag.THREAT_INTELLIGENCE_FEEDS -> _securitySettings.value.threatIntelligenceFeeds
          SecurityFlag.AI_THREAT_DETECTION -> _securitySettings.value.aiThreatDetection
          SecurityFlag.GOOGLE_SAFE_BROWSING -> _securitySettings.value.googleSafeBrowsing
          SecurityFlag.CRYPTOJACKING -> _securitySettings.value.cryptojacking
          SecurityFlag.DNS_REBINDING -> _securitySettings.value.dnsRebinding
          SecurityFlag.IDN_HOMOGRAPHS -> _securitySettings.value.idnHomographs
          SecurityFlag.TYPOSQUATTING -> _securitySettings.value.typosquatting
          SecurityFlag.DGA -> _securitySettings.value.dga
          SecurityFlag.NRD -> _securitySettings.value.nrd
          SecurityFlag.DDNS -> _securitySettings.value.ddns
          SecurityFlag.PARKING -> _securitySettings.value.parkedDomains
          SecurityFlag.CSAM -> _securitySettings.value.csam
        } == enabled
      }
    ) { key, pid ->
      apiService.updateSecurity(key, pid, request)
    }
  }

  suspend fun addBlockedTld(tld: String): Result<Unit> =
    mutateSection(
      section = SyncSection.SECURITY,
      operationName = "addBlockedTld",
      verify = { _securitySettings.value.blockedTlds.any { it.equals(tld, true) } }
    ) { key, pid ->
      apiService.addSecurityTld(key, pid, IdRequest(id = tld))
    }

  suspend fun removeBlockedTld(tld: String): Result<Unit> =
    mutateSection(
      section = SyncSection.SECURITY,
      operationName = "removeBlockedTld",
      verify = { _securitySettings.value.blockedTlds.none { it.equals(tld, true) } }
    ) { key, pid ->
      apiService.removeSecurityTld(key, pid, tld)
    }

  // =========================================================================
  // Privacy Updates
  // =========================================================================

  suspend fun setPrivacyFlag(flag: PrivacyFlag, enabled: Boolean): Result<Unit> {
    val request = when (flag) {
      PrivacyFlag.DISGUISED_TRACKERS -> PrivacyUpdateRequest(disguisedTrackers = enabled)
      PrivacyFlag.ALLOW_AFFILIATE_LINKS -> PrivacyUpdateRequest(allowAffiliateLinks = enabled)
    }

    return mutateSection(
      section = SyncSection.PRIVACY,
      operationName = "setPrivacyFlag",
      verify = {
        when (flag) {
          PrivacyFlag.DISGUISED_TRACKERS -> _privacySettings.value.disguisedTrackers
          PrivacyFlag.ALLOW_AFFILIATE_LINKS -> _privacySettings.value.allowAffiliates
        } == enabled
      }
    ) { key, pid ->
      apiService.updatePrivacy(key, pid, request)
    }
  }

  suspend fun toggleBlocklist(blocklistId: String): Result<Unit> = withSessionContext {
    val target = _privacySettings.value.blocklists.firstOrNull { it.id == blocklistId }
      ?: return@withSessionContext Result.failure(IllegalArgumentException(AppStrings.get(R.string.ui_0e194a1fda)))

    val activate = !target.active
    return@withSessionContext mutateSection(
      section = SyncSection.PRIVACY,
      operationName = "toggleBlocklist",
      verify = { (_privacySettings.value.blocklists.firstOrNull { it.id == blocklistId }?.active == true) == activate }
    ) { key, pid ->
      if (activate) {
        apiService.addBlocklist(key, pid, IdRequest(id = blocklistId))
      } else {
        apiService.removeBlocklist(key, pid, blocklistId)
      }
    }
  }

  suspend fun toggleNativeTracking(nativeId: String): Result<Unit> = withSessionContext {
    val target = _privacySettings.value.nativeTracking.firstOrNull { it.id == nativeId }
      ?: return@withSessionContext Result.failure(IllegalArgumentException(AppStrings.get(R.string.ui_bdcf657cbe)))

    val activate = !target.active
    return@withSessionContext mutateSection(
      section = SyncSection.PRIVACY,
      operationName = "toggleNativeTracking",
      verify = { (_privacySettings.value.nativeTracking.firstOrNull { it.id == nativeId }?.active == true) == activate }
    ) { key, pid ->
      if (activate) {
        apiService.addNativeTracking(key, pid, IdRequest(id = nativeId))
      } else {
        apiService.removeNativeTracking(key, pid, nativeId)
      }
    }
  }

  // =========================================================================
  // Parental Updates
  // =========================================================================

  suspend fun setParentalFlag(flag: ParentalFlag, enabled: Boolean): Result<Unit> {
    val request = when (flag) {
      ParentalFlag.SAFE_SEARCH -> ParentalControlUpdateRequest(safeSearch = enabled)
      ParentalFlag.YOUTUBE_RESTRICTED_MODE -> ParentalControlUpdateRequest(youtubeRestrictedMode = enabled)
      ParentalFlag.BLOCK_BYPASS -> ParentalControlUpdateRequest(blockBypass = enabled)
    }

    return mutateSection(
      section = SyncSection.PARENTAL,
      operationName = "setParentalFlag",
      verify = {
        when (flag) {
          ParentalFlag.SAFE_SEARCH -> _parentalControlSettings.value.safeSearch
          ParentalFlag.YOUTUBE_RESTRICTED_MODE -> _parentalControlSettings.value.youtubeRestrictedMode
          ParentalFlag.BLOCK_BYPASS -> _parentalControlSettings.value.blockBypass
        } == enabled
      }
    ) { key, pid ->
      apiService.updateParentalControl(key, pid, request)
    }
  }

  suspend fun toggleParentalService(serviceId: String): Result<Unit> = withSessionContext {
    val target = _parentalControlSettings.value.services.firstOrNull { it.id == serviceId }
      ?: return@withSessionContext Result.failure(IllegalArgumentException(AppStrings.get(R.string.ui_1705477f10)))

    val activate = !target.active
    return@withSessionContext mutateSection(
      section = SyncSection.PARENTAL,
      operationName = "toggleParentalService",
      verify = { (_parentalControlSettings.value.services.firstOrNull { it.id == serviceId }?.active == true) == activate }
    ) { key, pid ->
      if (activate) {
        apiService.addParentalService(
          key, pid, ParentalItemRequest(id = serviceId, active = true)
        )
      } else {
        apiService.removeParentalService(key, pid, serviceId)
      }
    }
  }

  suspend fun toggleParentalCategory(categoryId: String): Result<Unit> = withSessionContext {
    val target = _parentalControlSettings.value.categories.firstOrNull { it.id == categoryId }
      ?: return@withSessionContext Result.failure(IllegalArgumentException(AppStrings.get(R.string.ui_b9861c3200)))

    val activate = !target.active
    return@withSessionContext mutateSection(
      section = SyncSection.PARENTAL,
      operationName = "toggleParentalCategory",
      verify = { (_parentalControlSettings.value.categories.firstOrNull { it.id == categoryId }?.active == true) == activate }
    ) { key, pid ->
      if (activate) {
        apiService.addParentalCategory(
          key, pid, ParentalItemRequest(id = categoryId, active = true)
        )
      } else {
        apiService.removeParentalCategory(key, pid, categoryId)
      }
    }
  }

  // =========================================================================
  // Denylist & Allowlist
  // =========================================================================

  suspend fun addToDenylist(domain: String): Result<Unit> {
    val canonical = normalizeDomainRule(domain)
      ?: return Result.failure(IllegalArgumentException(AppStrings.get(R.string.ui_585a0d389f)))
    return mutateSection(
      section = SyncSection.DENYLIST,
      operationName = "addToDenylist",
      verify = { _denylist.value.any { normalizeDomainRule(it.domain) == canonical && it.active } }
    ) { key, pid ->
      apiService.addDenylist(
        key, pid, AllowDenyItemRequest(id = canonical, active = true)
      )
    }
  }

  suspend fun removeFromDenylist(domain: String): Result<Unit> =
    mutateSection(
      section = SyncSection.DENYLIST,
      operationName = "removeFromDenylist",
      verify = { _denylist.value.none { it.domain == domain } }
    ) { key, pid ->
      apiService.removeDenylist(key, pid, domain)
    }

  suspend fun toggleDenylistItem(domain: String): Result<Unit> = withSessionContext {
    val target = _denylist.value.firstOrNull { it.id == domain || it.domain == domain }
      ?: return@withSessionContext Result.failure(IllegalArgumentException(AppStrings.get(R.string.ui_75e8c7e60b)))

    val activate = !target.active
    return@withSessionContext mutateSection(
      section = SyncSection.DENYLIST,
      operationName = "toggleDenylistItem",
      verify = { (_denylist.value.firstOrNull { it.domain == target.domain }?.active == true) == activate }
    ) { key, pid ->
      apiService.toggleDenylist(
        key, pid, target.domain, AllowDenyActiveRequest(active = activate)
      )
    }
  }

  suspend fun addToAllowlist(domain: String): Result<Unit> {
    val canonical = normalizeDomainRule(domain)
      ?: return Result.failure(IllegalArgumentException(AppStrings.get(R.string.ui_585a0d389f)))
    return mutateSection(
      section = SyncSection.ALLOWLIST,
      operationName = "addToAllowlist",
      verify = { _allowlist.value.any { normalizeDomainRule(it.domain) == canonical && it.active } }
    ) { key, pid ->
      apiService.addAllowlist(
        key, pid, AllowDenyItemRequest(id = canonical, active = true)
      )
    }
  }

  suspend fun removeFromAllowlist(domain: String): Result<Unit> =
    mutateSection(
      section = SyncSection.ALLOWLIST,
      operationName = "removeFromAllowlist",
      verify = { _allowlist.value.none { it.domain == domain } }
    ) { key, pid ->
      apiService.removeAllowlist(key, pid, domain)
    }

  suspend fun toggleAllowlistItem(domain: String): Result<Unit> = withSessionContext {
    val target = _allowlist.value.firstOrNull { it.id == domain || it.domain == domain }
      ?: return@withSessionContext Result.failure(IllegalArgumentException(AppStrings.get(R.string.ui_e09828adaa)))

    val activate = !target.active
    return@withSessionContext mutateSection(
      section = SyncSection.ALLOWLIST,
      operationName = "toggleAllowlistItem",
      verify = { (_allowlist.value.firstOrNull { it.domain == target.domain }?.active == true) == activate }
    ) { key, pid ->
      apiService.toggleAllowlist(
        key, pid, target.domain, AllowDenyActiveRequest(active = activate)
      )
    }
  }

  // =========================================================================
  // Config & Logs
  // =========================================================================

  suspend fun setLogsEnabled(enabled: Boolean): Result<Unit> =
    mutateSection(
      section = SyncSection.SETTINGS,
      operationName = "setLogsEnabled",
      verify = { _configSettings.value.logsEnabled == enabled }
    ) { key, pid ->
      apiService.updateSettingsLogs(
        key, pid, SettingsLogsUpdateRequest(enabled = enabled)
      )
    }

  suspend fun setLogClientIps(enabled: Boolean): Result<Unit> =
    mutateSection(
      section = SyncSection.SETTINGS,
      operationName = "setLogClientIps",
      verify = { _configSettings.value.logClientIps == enabled }
    ) { key, pid ->
      apiService.updateSettingsLogs(
        key, pid, SettingsLogsUpdateRequest(
          drop = SettingsLogsDropDto(ip = !enabled)
        )
      )
    }

  suspend fun setLogDomains(enabled: Boolean): Result<Unit> =
    mutateSection(
      section = SyncSection.SETTINGS,
      operationName = "setLogDomains",
      verify = { _configSettings.value.logDomains == enabled }
    ) { key, pid ->
      apiService.updateSettingsLogs(
        key, pid, SettingsLogsUpdateRequest(
          drop = SettingsLogsDropDto(domain = !enabled)
        )
      )
    }

  suspend fun setLogRetention(retention: String): Result<Unit> {
    val seconds = LogRetentionCodec.toSeconds(retention)
      ?: return Result.failure(IllegalArgumentException(AppStrings.get(R.string.ui_50e35a68f0)))

    return mutateSection(
      section = SyncSection.SETTINGS,
      operationName = "setLogRetention",
      verify = { LogRetentionCodec.toSeconds(_configSettings.value.logRetention) == seconds }
    ) { key, pid ->
      apiService.updateSettingsLogs(
        key, pid, SettingsLogsUpdateRequest(retention = seconds)
      )
    }
  }

  suspend fun setLogStorageLocation(location: String): Result<Unit> {
    val code = when (location) {
      "İsviçre (CH)" -> "ch"
      "Avrupa Birliği (AB)" -> "eu"
      "Amerika Birleşik Devletleri (ABD)" -> "us"
      else -> return Result.failure(
        IllegalArgumentException(AppStrings.get(R.string.ui_a8512035f2))
      )
    }

    return mutateSection(
      section = SyncSection.SETTINGS,
      operationName = "setLogStorageLocation",
      verify = { _configSettings.value.logStorageLocation == location }
    ) { key, pid ->
      apiService.updateSettingsLogs(
        key, pid, SettingsLogsUpdateRequest(location = code)
      )
    }
  }

  suspend fun setBlockPage(enabled: Boolean): Result<Unit> =
    mutateSection(
      section = SyncSection.SETTINGS,
      operationName = "setBlockPage",
      verify = { _configSettings.value.blockPage == enabled }
    ) { key, pid ->
      apiService.updateSettingsBlockPage(
        key, pid, SettingsBlockPageUpdateRequest(enabled = enabled)
      )
    }

  suspend fun setPerformanceFlag(
    flag: SettingsPerformanceFlag,
    enabled: Boolean
  ): Result<Unit> {
    val request = when (flag) {
      SettingsPerformanceFlag.ECS -> SettingsPerformanceUpdateRequest(ecs = enabled)
      SettingsPerformanceFlag.CACHE_BOOST -> SettingsPerformanceUpdateRequest(cacheBoost = enabled)
      SettingsPerformanceFlag.CNAME_FLATTENING -> SettingsPerformanceUpdateRequest(cnameFlattening = enabled)
    }

    return mutateSection(
      section = SyncSection.SETTINGS,
      operationName = "setPerformanceFlag",
      verify = {
        when (flag) {
          SettingsPerformanceFlag.ECS -> _configSettings.value.ednsClientSubnet
          SettingsPerformanceFlag.CACHE_BOOST -> _configSettings.value.cacheBoost
          SettingsPerformanceFlag.CNAME_FLATTENING -> _configSettings.value.cnameFlattening
        } == enabled
      }
    ) { key, pid ->
      apiService.updateSettingsPerformance(key, pid, request)
    }
  }

  suspend fun setWeb3(enabled: Boolean): Result<Unit> =
    mutateSection(
      section = SyncSection.SETTINGS,
      operationName = "setWeb3",
      verify = { _configSettings.value.web3 == enabled }
    ) { key, pid ->
      apiService.updateSettings(
        key, pid, SettingsUpdateRequest(web3 = enabled)
      )
    }

  private fun extractDownloadUrl(rawJson: String): HttpUrl? {
    fun find(value: Any?): HttpUrl? = when (value) {
      is String -> value.toHttpUrlOrNull()?.takeIf { it.scheme == "https" && it.username.isEmpty() && it.password.isEmpty() }
      is org.json.JSONObject -> {
        value.keys().asSequence()
          .mapNotNull { key -> find(value.opt(key)) }
          .firstOrNull()
      }
      is org.json.JSONArray -> {
        (0 until value.length()).asSequence()
          .mapNotNull { index -> find(value.opt(index)) }
          .firstOrNull()
      }
      else -> null
    }

    return runCatching {
      find(org.json.JSONObject(rawJson))
    }.getOrNull()
  }

  suspend fun exportLogs(outputStream: OutputStream): Result<Unit> = withSessionContext {
    val snapshot = checkNotNull(currentCoroutineContext()[SessionContext]).snapshot
    val key = snapshot.apiKey
    val pid = snapshot.profileId
    if (key.isBlank() || pid.isBlank()) {
      return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_75ad86c6ba)))
    }
    try {
      val response = safeApiCall("getLogsDownloadLink") {
        apiService.getLogsDownloadLink(key, pid, redirect = 0)
      } ?: return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_7d6197fbd4)))
      if (!response.isSuccessful) {
        response.errorBody()?.close()
        return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.export_link_http, response.code())))
      }
      val raw = response.body()?.use { it.string() }
        ?: return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_8cb3b36c3c)))
      val url = extractDownloadUrl(raw)
        ?: return@withSessionContext Result.failure(IllegalStateException(AppStrings.get(R.string.ui_e51fbaad5c)))
      val request = Request.Builder().url(url).header("Accept", "*/*").build()
      downloadClient.newCall(request).withCancellableResponse { download ->
        ensureOperationActive()
        if (!download.isSuccessful) return@withCancellableResponse Result.failure(
          IllegalStateException(AppStrings.get(R.string.export_download_http, download.code)))
        val body = download.body ?: return@withCancellableResponse Result.failure(
          IllegalStateException(AppStrings.get(R.string.ui_90e9c703f0)))
        body.byteStream().use { input ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            ensureOperationActive()
            val read = input.read(buffer)
            if (read < 0) break
            ensureOperationActive()
            outputStream.write(buffer, 0, read)
          }
        }
        ensureOperationActive()
        outputStream.flush()
        ensureOperationActive()
        Result.success(Unit)
      }
    } catch (cancel: CancellationException) {
      throw cancel
    } catch (error: Exception) {
      Result.failure(IllegalStateException(AppStrings.get(R.string.ui_0813aca08b), error))
    }
  }

  suspend fun clearLogs(): Result<Unit> = withSessionContext {
    val key = _apiKey.value
    val pid = _activeProfileId.value
    if (key.isBlank() || pid.isBlank()) {
      return@withSessionContext Result.failure(
        IllegalStateException(AppStrings.get(R.string.ui_75ad86c6ba))
      )
    }

    val response = safeApiCall("clearLogs") {
      apiService.clearLogs(key, pid)
    } ?: return@withSessionContext Result.failure(
      IllegalStateException(AppStrings.get(R.string.ui_b5f608bed2))
    )

    val apiError = response.body()?.errors?.firstOrNull()?.detail
    if (!response.isMutationAccepted()) {
      return@withSessionContext Result.failure(
        IllegalStateException(apiError ?: AppStrings.get(R.string.ui_7161dae00a))
      )
    }

    commitCurrentSession {
      // Prevent a log snapshot started before this successful clear from restoring old history.
      logsRefreshRequest = null
      _logs.value = emptyList()
      nextLogsCursor = null
      logsSnapshotGeneration++
      _logsPaginationState.value = LogsPaginationState()
    }
    return@withSessionContext Result.success(Unit)
  }

  private suspend fun refreshAfterLogFilterChange(updateFilter: () -> Boolean): Boolean = withSessionContext {
    if (_apiKey.value.isBlank() || _activeProfileId.value.isBlank()) {
      commitCurrentSession { _logsPaginationState.value = LogsPaginationState() }
      return@withSessionContext false
    }
    val wasStreaming = _isLiveStreaming.value
    val changed = commitCurrentSession {
      if (!updateFilter()) return@commitCurrentSession false
      logsRefreshRequest = requestSequence.incrementAndGet()
      logsSnapshotGeneration++
      logsStreamSeedId = null
      _logs.value = emptyList()
      nextLogsCursor = null
      _logsPaginationState.value = LogsPaginationState(isLoading = true)
      true
    }
    if (!changed) return@withSessionContext true
    val refreshed = refreshLogsFromApi()
    if (wasStreaming) startLogsStream()
    refreshed
  }

  suspend fun setLogsDeviceFilter(deviceId: String?): Boolean {
    val normalized = deviceId?.trim()?.takeIf { it.isNotEmpty() }
    return refreshAfterLogFilterChange {
      if (_logsDeviceFilter.value == normalized) false
      else { _logsDeviceFilter.value = normalized; true }
    }
  }

  suspend fun setLogsStatusFilter(status: String?): Boolean {
    val normalized = status?.trim()?.lowercase(Locale.ROOT)?.takeIf {
      it in setOf("default", "error", "blocked", "allowed")
    }
    return refreshAfterLogFilterChange {
      if (_logsStatusFilter.value == normalized) false
      else { _logsStatusFilter.value = normalized; true }
    }
  }

  suspend fun setLogsSearchFilter(search: String?): Boolean {
    val normalized = search?.trim()?.takeIf { it.isNotEmpty() }
    return refreshAfterLogFilterChange {
      if (_logsSearchFilter.value == normalized) false
      else { _logsSearchFilter.value = normalized; true }
    }
  }

  suspend fun refreshLogsFromApi(limit: Int = 100): Boolean = withSessionContext {
    val key = _apiKey.value
    val pid = _activeProfileId.value
    if (key.isBlank() || pid.isBlank()) return@withSessionContext false

    val deviceFilter = _logsDeviceFilter.value
    val statusFilter = _logsStatusFilter.value
    val searchFilter = _logsSearchFilter.value
    val token = requestSequence.incrementAndGet()
    val before = commitCurrentSession {
      logsRefreshRequest = token
      logsSnapshotGeneration++
      _logsPaginationState.value = _logsPaginationState.value.copy(isLoading = true, error = null)
      Triple(_logs.value.toList(), logsStreamSeedId, logsSnapshotGeneration)
    }
    val logsResp = safeApiCall("refreshLogsFromApi") {
      apiService.getLogs(key, pid, limit = limit, search = searchFilter, device = deviceFilter, status = statusFilter, raw = 1)
    } ?: return@withSessionContext failLogsPageRequest(token)

    val body = logsResp.body()
    if (!logsResp.isSuccessful || body == null || body.hasApiErrors() || body.data == null) {
      return@withSessionContext failLogsPageRequest(token)
    }

    commitCurrentSession {
      if (logsRefreshRequest != token || logsSnapshotGeneration != before.third ||
        _logsDeviceFilter.value != deviceFilter || _logsStatusFilter.value != statusFilter ||
        _logsSearchFilter.value != searchFilter) return@commitCurrentSession false
      // A stream may have advanced while this authoritative snapshot was in flight.
      if (logsStreamSeedId == before.second) logsStreamSeedId = body.meta?.stream?.id
      logsRefreshRequest = null
      val previousIds = before.first.mapTo(HashSet()) { it.id }
      val intervening = _logs.value.filter { it.id !in previousIds }
      val fetchedLogs = parseLogsResponse(body.data)
      val mergedLogs = (intervening + fetchedLogs + before.first).distinctBy { it.id }
      val retained = mergedLogs.take(MAX_RETAINED_LOG_ENTRIES)
      val retentionLimitReached = _logsPaginationState.value.retentionLimitReached ||
        mergedLogs.size > retained.size ||
        (retained.size == MAX_RETAINED_LOG_ENTRIES && body.meta?.pagination?.cursor != null)
      nextLogsCursor = body.meta?.pagination?.cursor
        ?.takeIf { retained.size < MAX_RETAINED_LOG_ENTRIES && !retentionLimitReached }
      _logs.value = retained
      _logsPaginationState.value = LogsPaginationState(
        hasMore = nextLogsCursor != null,
        retentionLimitReached = retentionLimitReached
      )
      true
    }
  }

  suspend fun loadMoreLogsFromApi(limit: Int = 100): Boolean = withSessionContext {
    val key = _apiKey.value
    val pid = _activeProfileId.value
    if (key.isBlank() || pid.isBlank()) return@withSessionContext false

    val cursor = nextLogsCursor ?: return@withSessionContext false
    val deviceFilter = _logsDeviceFilter.value
    val statusFilter = _logsStatusFilter.value
    val searchFilter = _logsSearchFilter.value
    val token = requestSequence.incrementAndGet()
    val before = commitCurrentSession {
      if (_logsPaginationState.value.isLoading) return@commitCurrentSession null
      logsRefreshRequest = token
      logsSnapshotGeneration++
      _logsPaginationState.value = _logsPaginationState.value.copy(isLoading = true, error = null)
      _logs.value.toList() to logsSnapshotGeneration
    } ?: return@withSessionContext false

    val logsResp = safeApiCall("loadMoreLogsFromApi") {
      apiService.getLogs(key, pid, limit = limit, search = searchFilter, device = deviceFilter, status = statusFilter, cursor = cursor, raw = 1)
    } ?: return@withSessionContext failLogsPageRequest(token)

    val body = logsResp.body()
    if (!logsResp.isSuccessful || body == null || body.hasApiErrors() || body.data == null) {
      return@withSessionContext failLogsPageRequest(token)
    }

    commitCurrentSession {
      if (logsRefreshRequest != token || logsSnapshotGeneration != before.second ||
        _logsDeviceFilter.value != deviceFilter || _logsStatusFilter.value != statusFilter ||
        _logsSearchFilter.value != searchFilter) return@commitCurrentSession false
      val responseCursor = body.meta?.pagination?.cursor?.takeIf { it != cursor }
      logsRefreshRequest = null
      val intervening = _logs.value.filter { current -> before.first.none { it.id == current.id } }
      val mergedLogs = (intervening + before.first + parseLogsResponse(body.data)).distinctBy { it.id }
      val retained = mergedLogs.take(MAX_RETAINED_LOG_ENTRIES)
      val retentionLimitReached = _logsPaginationState.value.retentionLimitReached ||
        mergedLogs.size > retained.size ||
        (retained.size == MAX_RETAINED_LOG_ENTRIES && responseCursor != null)
      nextLogsCursor = responseCursor
        ?.takeIf { retained.size < MAX_RETAINED_LOG_ENTRIES && !retentionLimitReached }
      _logs.value = retained
      _logsPaginationState.value = LogsPaginationState(
        hasMore = nextLogsCursor != null,
        retentionLimitReached = retentionLimitReached
      )
      true
    }
  }

  private suspend fun failLogsPageRequest(token: Long): Boolean = commitCurrentSession {
    if (logsRefreshRequest != token) return@commitCurrentSession false
    logsRefreshRequest = null
    _logsPaginationState.value = _logsPaginationState.value.copy(
      isLoading = false,
      error = AppStrings.get(R.string.logs_page_error)
    )
    false
  }

  fun setLiveStreaming(enabled: Boolean) {
    _isLiveStreaming.value = enabled
    if (enabled) {
      startLogsStream()
    } else {
      stopLogsStream()
    }
  }

  fun startAnalyticsPolling() {
    analyticsPollingJob?.cancel()
    analyticsPollingJob = repoScope.launch {
      var nextDelayMs = SyncPolicy.ANALYTICS_POLL_MS
      while (isActive) {
        val request = synchronized(sessionLock) {
          beginAnalyticsRequest(_apiKey.value, _activeProfileId.value, analyticsRequest?.device, analyticsRequest?.from)
        }
        val success = fetchAnalytics(request)
        nextDelayMs = SyncPolicy.nextDelay(
          success = success,
          currentDelayMs = nextDelayMs,
          baseDelayMs = SyncPolicy.ANALYTICS_POLL_MS,
          maxDelayMs = SyncPolicy.ANALYTICS_MAX_BACKOFF_MS
        )
        delay(nextDelayMs)
      }
    }
  }

  fun stopAnalyticsPolling() {
    analyticsPollingJob?.cancel()
    analyticsPollingJob = null
  }

  fun startLogsStream() {
    val snapshot = captureSession()
    val key = snapshot.apiKey
    val pid = snapshot.profileId
    if (key.isBlank() || pid.isBlank()) return
    val seedDeviceFilter = _logsDeviceFilter.value
    val seedStatusFilter = _logsStatusFilter.value
    val seedSearchFilter = _logsSearchFilter.value
    val seedGeneration = synchronized(sessionLock) { logsSnapshotGeneration }

    stopLogsStream()
    _isLiveStreaming.value = true

    streamJob = repoScope.launch(Dispatchers.IO) {
      withSessionContext(snapshot = snapshot) {
        var lastId: String? = logsStreamSeedId

        if (lastId == null) {
          val seedResp = safeApiCall("seedLogsStream") {
            apiService.getLogs(key, pid, limit = 100, search = seedSearchFilter, device = seedDeviceFilter, status = seedStatusFilter, raw = 1)
          }
          val seedBody = seedResp?.body()
          if (seedResp?.isSuccessful == true && seedBody.isSemanticallySuccessful()) {
            commitCurrentSession {
              if (_logsDeviceFilter.value == seedDeviceFilter && _logsStatusFilter.value == seedStatusFilter &&
                _logsSearchFilter.value == seedSearchFilter &&
                logsSnapshotGeneration == seedGeneration && logsRefreshRequest == null) {
                val seededLogs = parseLogsResponse(seedBody?.data.orEmpty())
                val retained = seededLogs.take(MAX_RETAINED_LOG_ENTRIES)
                logsStreamSeedId = seedBody?.meta?.stream?.id
                _logs.value = retained
                val apiCursor = seedBody?.meta?.pagination?.cursor
                nextLogsCursor = apiCursor?.takeIf { retained.size < MAX_RETAINED_LOG_ENTRIES }
                _logsPaginationState.value = LogsPaginationState(
                  hasMore = nextLogsCursor != null,
                  retentionLimitReached = seededLogs.size > retained.size ||
                    (retained.size == MAX_RETAINED_LOG_ENTRIES && apiCursor != null)
                )
              }
              lastId = logsStreamSeedId ?: seedBody?.meta?.stream?.id
            }
          }
        }

        var reconnectDelayMs = 2_000L
        while (isActive) {
          try {
            val req = buildLogsStreamRequest(pid, key, lastId, seedDeviceFilter, seedStatusFilter, seedSearchFilter)
            streamClient.newCall(req).withCancellableResponse { response ->
              if (!response.isSuccessful) {
                throw java.io.IOException("Logs stream HTTP ${response.code}")
              }
              val body = response.body
                ?: throw java.io.IOException("Logs stream body is empty")

              reconnectDelayMs = 2_000L
              consumeSseStream(body) { newId ->
                commitCurrentSession {
                  lastId = newId
                  logsStreamSeedId = newId
                }
              }
            }

            if (isActive) delay(500L)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Stream error: ${e.message}")
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(60_000L)
          }
        }
      }
    }
  }

  private fun buildLogsStreamRequest(
    pid: String,
    key: String,
    lastId: String?,
    device: String?,
    status: String?,
    search: String?
  ): Request {
    val url = requireNotNull("https://api.nextdns.io".toHttpUrlOrNull()).newBuilder()
      .addPathSegment("profiles")
      .addPathSegment(pid)
      .addPathSegment("logs")
      .addPathSegment("stream")
      .apply {
        lastId?.let { addQueryParameter("id", it) }
        device?.let { addQueryParameter("device", it) }
        status?.let { addQueryParameter("status", it) }
        search?.let { addQueryParameter("search", it) }
        addQueryParameter("raw", "1")
      }
      .build()
    return Request.Builder()
      .url(url)
      .header("X-Api-Key", key)
      .header("Accept", "text/event-stream")
      .build()
  }

  private suspend fun consumeSseStream(responseBody: okhttp3.ResponseBody, onNewId: suspend (String) -> Unit) {
    val source = responseBody.source()
    while (currentCoroutineContext().isActive && !source.exhausted()) {
      val line = source.readUtf8Line() ?: break
      processSseLine(line, onNewId)
    }
  }

  private suspend fun processSseLine(line: String, onNewId: suspend (String) -> Unit) {
    if (line.startsWith("id: ")) {
      onNewId(line.substring(4))
      return
    }
    if (!line.startsWith("data: ")) return

    val jsonStr = line.substring(6)
    val entry = parseSseLogEntry(jsonStr) ?: return
    emitLogEntry(entry)
  }

  private suspend fun parseSseLogEntry(jsonStr: String): DnsLogEntry? {
    if (jsonStr.isBlank()) return null
    val logDto = safeApiCall("sseJsonParse") {
      NextDnsNetworkClient.moshi.adapter(DnsLogDto::class.java).fromJson(jsonStr)
    } ?: return null

    return commitCurrentSession {
      val devId = logDto.device?.id
      val candidateName = logDto.device?.name?.takeIf { it.isNotBlank() }
        ?: logDto.deviceName?.takeIf { it.isNotBlank() }
        ?: logDto.deviceNameSnake?.takeIf { it.isNotBlank() }
        ?: (if (!devId.isNullOrBlank()) _knownDeviceIdToName[devId] else null)
      val devModel = logDto.device?.model
      val devName = DeviceLabelResolver.resolve(
        candidateName,
        devModel,
        devId,
        AppStrings.get(R.string.device_unidentified)
      )
      val status = normalizedLogStatus(logDto.status)

      if (!matchesLogsDeviceFilter(devId) || !matchesLogsStatusFilter(status)) return@commitCurrentSession null

      rememberLogDevice(devId, devName, devModel)

      val reasonObj = logDto.reasons?.firstOrNull()
      val blockReason = formatBlockReason(reasonObj?.id, reasonObj?.name)

      return@commitCurrentSession DnsLogEntry(
        id = logDto.id?.takeIf { it.isNotBlank() } ?: stableLogId(
          logDto.timestamp?.toString().orEmpty(), logDto.domain.orEmpty(), devId.orEmpty(), logDto.status.orEmpty(),
          logDto.protocol.orEmpty(), logDto.clientIp ?: logDto.clientIpSnake.orEmpty(),
          logDto.responseTime ?: logDto.responseTimeSnake
        ),
        timestamp = logDto.timestamp?.toString() ?: "",
        domain = logDto.domain?.takeIf { it.isNotBlank() } ?: logDto.root?.takeIf { it.isNotBlank() } ?: logDto.rootDomain?.takeIf { it.isNotBlank() } ?: "",
        rootDomain = logDto.root?.takeIf { it.isNotBlank() } ?: logDto.rootDomain?.takeIf { it.isNotBlank() } ?: logDto.domain.orEmpty(),
        deviceId = devId,
        tracker = logDto.tracker,
        encrypted = logDto.encrypted,
        client = logDto.client,
        clientIp = logDto.clientIp ?: logDto.clientIpSnake,
        deviceName = devName,
        blocked = status == "blocked",
        status = status,
        blockReason = if (status == "blocked") blockReason else null,
        protocol = logDto.protocol ?: "",
        dnssec = logDto.dnssec,
        responseTimeMs = logDto.responseTime ?: logDto.responseTimeSnake,
        deviceModel = devModel
      )
    }
  }

  private suspend fun emitLogEntry(entry: DnsLogEntry) {
    withContext(Dispatchers.Main) {
      commitCurrentSession {
        if (!matchesLogsDeviceFilter(entry.deviceId) || !matchesLogsStatusFilter(entry.status)) return@commitCurrentSession
        val current = _logs.value
        val isDuplicate = current.any { it.id == entry.id }
        if (!isDuplicate) {
          val updated = listOf(entry) + current
          _logs.value = updated.take(MAX_RETAINED_LOG_ENTRIES)
          if (updated.size > MAX_RETAINED_LOG_ENTRIES) {
            nextLogsCursor = null
            _logsPaginationState.value = _logsPaginationState.value.copy(
              hasMore = false,
              retentionLimitReached = true
            )
          }
        }
      }
    }
  }

  private fun matchesLogsDeviceFilter(deviceId: String?): Boolean = when (val selected = _logsDeviceFilter.value) {
    null -> true
    NEXTDNS_UNIDENTIFIED_DEVICE_ID -> deviceId.isNullOrBlank() || deviceId == NEXTDNS_UNIDENTIFIED_DEVICE_ID
    else -> deviceId == selected
  }

  private fun matchesLogsStatusFilter(status: String): Boolean =
    _logsStatusFilter.value?.let { it == status } ?: true

  fun stopLogsStream() {
    streamJob?.cancel()
    streamJob = null
    _isLiveStreaming.value = false
  }

  // =========================================================================
  // Analytics Parsing Helper Functions
  // =========================================================================

  private fun Response<out NextDnsApiResponse<*>>?.isUsableApiResponse(): Boolean =
    this?.isSuccessful == true && this?.body().isSemanticallySuccessful()

  private fun parseStatusMetrics(
    response: Response<NextDnsApiResponse<List<AnalyticsStatusItem>>>?,
    fallbackTotal: Long = 0L,
    fallbackBlocked: Long = 0L
  ): Pair<Long, Long> {
    if (!response.isUsableApiResponse()) return Pair(fallbackTotal, fallbackBlocked)
    val statuses = response?.body()?.data ?: emptyList()
    val blockedQueries = statuses.find { it.status == "blocked" }?.queries ?: 0L
    val allQueries = statuses.sumOf { it.queries ?: 0L }
    return Pair(allQueries, blockedQueries)
  }

  private fun parseTopDevices(
    response: Response<NextDnsApiResponse<List<AnalyticsDeviceItem>>>?
  ): List<DeviceMetric> {
    if (!response.isUsableApiResponse()) return _analytics.value.topDevices
    val devices = response?.body()?.data?.map {
      val id = it.id ?: ""
      val name = DeviceLabelResolver.resolve(
        it.name, it.model, id, AppStrings.get(R.string.device_unidentified)
      )
      rememberLogDevice(id, name, it.model)
      DeviceMetric(id = id, name = name, queries = it.queries ?: 0L, model = it.model.orEmpty())
    } ?: _analytics.value.topDevices
    updateKnownDevices(devices.map { it.name })
    return devices
  }

  private fun parseTopDomains(
    response: Response<NextDnsApiResponse<List<AnalyticsDomainItem>>>?,
    fallback: List<DomainMetric> = emptyList()
  ): List<DomainMetric> {
    if (!response.isUsableApiResponse()) return fallback
    return response?.body()?.data?.mapNotNull {
      val dom = it.domain ?: it.root
      if (dom.isNullOrBlank()) null
      else DomainMetric(domain = dom, queries = it.queries ?: 0L)
    } ?: fallback
  }

  private fun parseBlockedReasons(
    response: Response<NextDnsApiResponse<List<AnalyticsReasonItem>>>?
  ): Map<String, Long> {
    if (!response.isUsableApiResponse()) return _analytics.value.topBlockedReasons
    val rMap = mutableMapOf<String, Long>()
    response?.body()?.data?.forEach { item ->
      val formatted = formatBlockReason(item.id, item.name)
      val cur = rMap.getOrDefault(formatted, 0L)
      rMap[formatted] = cur + (item.queries ?: 0L)
    }
    return rMap.toList().sortedByDescending { it.second }.toMap()
  }

  private fun parseGafamMetrics(
    response: Response<NextDnsApiResponse<List<AnalyticsItemDto>>>?,
    totalQueries: Long
  ): Map<String, Pair<Double, Long>> {
    if (!response.isUsableApiResponse()) return _analytics.value.gafamMetrics
    val gafamMetrics = mutableMapOf<String, Pair<Double, Long>>()
    if (response.isUsableApiResponse()) {
      val data = response?.body()?.data ?: emptyList()
      data.forEach { item ->
        val count = item.queries ?: 0L
        val pct = if (totalQueries > 0) ((count.toDouble() / totalQueries) * 100.0).coerceIn(0.0, 100.0) else 0.0
        val companyName = item.company ?: item.name ?: item.id ?: AppStrings.get(R.string.ui_6cceb67979)
        gafamMetrics[companyName] = Pair(pct, count)
      }
    }
    return gafamMetrics
  }

  private fun parseCountryMetrics(
    response: Response<NextDnsApiResponse<List<AnalyticsItemDto>>>?,
    totalQueries: Long
  ): List<CountryTrafficMetric> {
    if (!response.isUsableApiResponse()) return _analytics.value.topCountries
    return response?.body()?.data.orEmpty().mapNotNull { item ->
      val code = item.code?.uppercase(Locale.ROOT)?.takeIf { it.matches(Regex("[A-Z]{2}")) }
        ?: return@mapNotNull null
      val queries = item.queries?.coerceAtLeast(0L) ?: 0L
      val percentage = if (totalQueries > 0) {
        ((queries.toDouble() / totalQueries) * 100.0).coerceIn(0.0, 100.0)
      } else {
        0.0
      }
      CountryTrafficMetric(
        code = code,
        queries = queries,
        percentage = percentage,
        domains = item.domains.orEmpty().asSequence().map(String::trim)
          .filter(String::isNotEmpty).distinct().take(5).toList()
      )
    }.sortedByDescending(CountryTrafficMetric::queries)
  }

  private fun parseDnssecPercentage(
    response: Response<NextDnsApiResponse<List<AnalyticsItemDto>>>?,
    totalQueries: Long
  ): Double {
    if (!response.isUsableApiResponse()) return _analytics.value.dnssecPercentage.toDouble()
    val items = response?.body()?.data ?: emptyList()
    val validated = items.find { it.validated == true || it.id == "validated" || it.id == "true" }?.queries ?: 0L
    return if (totalQueries > 0) ((validated.toDouble() / totalQueries) * 100.0).coerceIn(0.0, 100.0) else 0.0
  }

  private fun parseEncryptionPercentage(
    response: Response<NextDnsApiResponse<List<AnalyticsItemDto>>>?,
    totalQueries: Long
  ): Double {
    if (!response.isUsableApiResponse()) return _analytics.value.encryptedDnsPercentage.toDouble()
    val items = response?.body()?.data ?: emptyList()
    val encrypted = items.find { it.encrypted == true || it.id == "encrypted" || it.id == "true" }?.queries ?: 0L
    return if (totalQueries > 0) ((encrypted.toDouble() / totalQueries) * 100.0).coerceIn(0.0, 100.0) else 0.0
  }

  private fun parseProtocolMetrics(
    response: Response<NextDnsApiResponse<List<AnalyticsProtocolItem>>>?
  ): List<ProtocolMetric> {
    if (!response.isUsableApiResponse()) return _analytics.value.protocols
    return response?.body()?.data.orEmpty().mapNotNull { item ->
      val protocol = item.protocol?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      ProtocolMetric(protocol = protocol, queries = item.queries ?: 0L)
    }
  }

  private fun parseQueryTypeMetrics(
    response: Response<NextDnsApiResponse<List<AnalyticsQueryTypeItem>>>?
  ): List<QueryTypeMetric> {
    if (!response.isUsableApiResponse()) return _analytics.value.queryTypes
    return response?.body()?.data.orEmpty().mapNotNull { item ->
      val name = item.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      QueryTypeMetric(type = item.type, name = name, queries = item.queries ?: 0L)
    }
  }

  private fun parseIpVersionMetrics(
    response: Response<NextDnsApiResponse<List<AnalyticsIpVersionItem>>>?
  ): List<IpVersionMetric> {
    if (!response.isUsableApiResponse()) return _analytics.value.ipVersions
    return response?.body()?.data.orEmpty().mapNotNull { item ->
      val version = item.version ?: return@mapNotNull null
      IpVersionMetric(version = version, queries = item.queries ?: 0L)
    }
  }

  private fun parseIpMetrics(
    response: Response<NextDnsApiResponse<List<AnalyticsIpItem>>>?
  ): List<IpMetric> {
    if (!response.isUsableApiResponse()) return _analytics.value.topIps
    return response?.body()?.data.orEmpty().mapNotNull { item ->
      val ip = item.ip?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      IpMetric(
        ip = ip,
        queries = item.queries ?: 0L,
        cellular = item.network?.cellular,
        vpn = item.network?.vpn,
        isp = item.network?.isp,
        asn = item.network?.asn,
        countryCode = item.geo?.countryCode,
        country = item.geo?.country,
        city = item.geo?.city,
        latitude = item.geo?.latitude,
        longitude = item.geo?.longitude
      )
    }
  }

  // =========================================================================
  // Main Analytics Fetching Function
  // =========================================================================

  private fun beginAnalyticsRequest(key: String, profileId: String, device: String?, from: String?): AnalyticsRequest = synchronized(sessionLock) {
    val session = captureSession()
    if (session.apiKey != key || session.profileId != profileId) throw CancellationException("Analytics session changed")
    AnalyticsRequest(session, device, from).also { analyticsRequest = it }
  }

  suspend fun fetchAnalytics(key: String, profileId: String, device: String? = null, from: String? = null): Boolean =
    fetchAnalytics(beginAnalyticsRequest(key, profileId, device, from))

  private suspend fun fetchAnalytics(request: AnalyticsRequest): Boolean = withSessionContext(snapshot = request.session) {
    val key = request.session.apiKey
    val profileId = request.session.profileId
    val device = request.device
    val from = request.from
    if (key.isBlank() || profileId.isBlank()) return@withSessionContext false

    val devParam = if (device == "Tüm cihazlar" || device.isNullOrBlank()) {
      null
    } else {
      val trimmed = device.trim()
      _knownDeviceNameToId[trimmed]
        ?: _knownDeviceNameToId.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }?.value
        ?: _analytics.value.topDevices.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.id?.takeIf { it.isNotBlank() }
        ?: if (trimmed.equals("Bilinmeyen Cihaz", ignoreCase = true) || trimmed.equals("Bilinmeyen", ignoreCase = true) || trimmed == "__UNIDENTIFIED__") {
             "__UNIDENTIFIED__"
           } else {
             trimmed
           }
    }

    val fromParam = when {
      from.isNullOrBlank() -> "-30d"
      from.equals("Son 1 Saat", ignoreCase = true) || from.equals("1h", ignoreCase = true) || from.equals("-1h", ignoreCase = true) -> "-1h"
      from.equals("Son 24 Saat", ignoreCase = true) || from.equals("Son 24 saat", ignoreCase = true) || from.equals("24h", ignoreCase = true) || from.equals("-24h", ignoreCase = true) -> "-24h"
      from.equals("Son 7 Gün", ignoreCase = true) || from.equals("Son 7 gün", ignoreCase = true) || from.equals("7d", ignoreCase = true) || from.equals("-7d", ignoreCase = true) -> "-7d"
      from.equals("Son 30 Gün", ignoreCase = true) || from.equals("Son 30 gün", ignoreCase = true) || from.equals("30d", ignoreCase = true) || from.equals("-30d", ignoreCase = true) -> "-30d"
      from.equals("Son 3 Ay", ignoreCase = true) || from.equals("Son 3 ay", ignoreCase = true) || from.equals("Son 90 Gün", ignoreCase = true) || from.equals("Son 90 gün", ignoreCase = true) || from.equals("90d", ignoreCase = true) || from.equals("-90d", ignoreCase = true) -> "-90d"
      else -> "-30d"
    }

    val toParam = "now"

    val statusResp = safeApiCall("getAnalyticsStatus") {
      apiService.getAnalyticsStatus(
        key, profileId, devParam, fromParam, to = toParam
      )
    }
    val devicesResp = safeApiCall("getAnalyticsDevices") {
      apiService.getAnalyticsDevices(
        key, profileId, null, fromParam, to = toParam, limit = 500
      )
    }
    val allowedDomainsResp = safeApiCall("getAllowedDomains") {
      apiService.getAnalyticsDomains(
        key, profileId, devParam, fromParam,
        status = "default", to = toParam, limit = 50
      )
    }
    val blockedDomainsResp = safeApiCall("getBlockedDomains") {
      apiService.getAnalyticsDomains(
        key, profileId, devParam, fromParam,
        status = "blocked", to = toParam, limit = 50
      )
    }
    val rootDomainsResp = safeApiCall("getRootDomains") {
      apiService.getAnalyticsDomains(
        key, profileId, devParam, fromParam,
        root = true, to = toParam, limit = 50
      )
    }
    val reasonsResp = safeApiCall("getReasons") {
      apiService.getAnalyticsReasons(
        key, profileId, devParam, fromParam, to = toParam, limit = 100
      )
    }
    val companiesResp = safeApiCall("getCompanies") {
      apiService.getAnalyticsDestinations(
        key, profileId, devParam, fromParam,
        type = "gafam", to = toParam, limit = 50
      )
    }
    val destinationsResp = safeApiCall("getDestinations") {
      apiService.getAnalyticsDestinations(
        key, profileId, devParam, fromParam,
        type = "countries", to = toParam, limit = 50
      )
    }
    val dnssecResp = safeApiCall("getDnssec") {
      apiService.getAnalyticsDnssec(
        key, profileId, devParam, fromParam, to = toParam
      )
    }
    val encryptionResp = safeApiCall("getEncryption") {
      apiService.getAnalyticsEncryption(
        key, profileId, devParam, fromParam, to = toParam
      )
    }
    val protocolsResp = safeApiCall("getProtocols") {
      apiService.getAnalyticsProtocols(
        key, profileId, devParam, fromParam, to = toParam, limit = 50
      )
    }
    val queryTypesResp = safeApiCall("getQueryTypes") {
      apiService.getAnalyticsQueryTypes(
        key, profileId, devParam, fromParam, to = toParam, limit = 50
      )
    }
    val ipVersionsResp = safeApiCall("getIpVersions") {
      apiService.getAnalyticsIpVersions(
        key, profileId, devParam, fromParam, to = toParam, limit = 10
      )
    }
    val ipsResp = safeApiCall("getIps") {
      apiService.getAnalyticsIps(
        key, profileId, devParam, fromParam, to = toParam, limit = 50
      )
    }

    commitCurrentSession {
      if (analyticsRequest !== request) return@commitCurrentSession false
      val responses = listOf(statusResp, devicesResp, allowedDomainsResp, blockedDomainsResp,
        rootDomainsResp, reasonsResp, companiesResp, destinationsResp, dnssecResp,
        encryptionResp, protocolsResp, queryTypesResp, ipVersionsResp, ipsResp)
      val successfulResponses = responses.map {
        it.isUsableApiResponse() && it?.body()?.data != null
      }
      if (successfulResponses.none { it }) {
        _analyticsErrorMessage.value = AppStrings.get(R.string.ui_d4c19c0ba4)
        return@commitCurrentSession false
      }

      val previousAnalytics = _analytics.value
      val (totalQueries, blockedQueries) = parseStatusMetrics(
        statusResp,
        fallbackTotal = previousAnalytics.totalQueries,
        fallbackBlocked = previousAnalytics.blockedQueries
      )
      val topDevices = parseTopDevices(devicesResp)
      val topAllowedDomains = parseTopDomains(allowedDomainsResp, previousAnalytics.topAllowedDomains)
      val topBlockedDomains = parseTopDomains(blockedDomainsResp, previousAnalytics.topBlockedDomains)
      val topDomains = parseTopDomains(rootDomainsResp, previousAnalytics.topDomains)
      val topBlockedReasons = parseBlockedReasons(reasonsResp)
      val protocols = parseProtocolMetrics(protocolsResp)
      val queryTypes = parseQueryTypeMetrics(queryTypesResp)
      val ipVersions = parseIpVersionMetrics(ipVersionsResp)
      val topIps = parseIpMetrics(ipsResp)
      val gafamMetrics = parseGafamMetrics(companiesResp, totalQueries)
      val topCountries = parseCountryMetrics(destinationsResp, totalQueries)
      val dnssecPct = parseDnssecPercentage(dnssecResp, totalQueries)
      val encPct = parseEncryptionPercentage(encryptionResp, totalQueries)

      val blockRate = if (totalQueries > 0) (blockedQueries.toDouble() / totalQueries) * 100 else 0.0

      val updatedAnalytics = AnalyticsSummary(
        totalQueries = totalQueries,
        blockedQueries = blockedQueries,
        blockRate = blockRate,
        blockedPercentage = blockRate.toFloat(),
        topAllowedDomains = topAllowedDomains,
        topBlockedDomains = topBlockedDomains,
        topBlockedReasons = topBlockedReasons,
        topDevices = topDevices,
        topDomains = topDomains,
        protocols = protocols,
        queryTypes = queryTypes,
        ipVersions = ipVersions,
        topIps = topIps,
        gafamMetrics = gafamMetrics,
        encryptedDnsPercentage = encPct.toFloat(),
        dnssecPercentage = dnssecPct.toFloat(),
        topCountries = topCountries
      )

      _analytics.value = updatedAnalytics
      _analyticsLastSuccessAt.value = System.currentTimeMillis()
      _analyticsErrorMessage.value = if (successfulResponses.all { it }) {
        null
      } else {
        AppStrings.get(R.string.analytics_partial_failure_notice)
      }
      true
    }
  }

  fun formatBlockReason(rawId: String?, rawName: String?): String {
    val preservedId = rawId?.trim().orEmpty()
    val id = preservedId.lowercase(Locale.ROOT)
    val name = rawName?.trim() ?: ""

    // 1. Name based translations / cleanups
    if (name.isNotBlank()) {
      when {
        name.equals("NextDNS Ads & Trackers Blocklist", ignoreCase = true) -> return AppStrings.get(R.string.ui_890f81a670)
        name.equals("Disguised Third-Party Trackers", ignoreCase = true) || name.equals("Disguised Trackers", ignoreCase = true) -> return AppStrings.get(R.string.ui_63f2dd13cc)
        name.equals("Block Bypass Methods", ignoreCase = true) || name.equals("Bypass Methods", ignoreCase = true) -> return AppStrings.get(R.string.ui_7396ea2940)
        name.equals("Denylist", ignoreCase = true) || name.equals("Blacklist", ignoreCase = true) -> return AppStrings.get(R.string.ui_8aa4ccc961)
        name.equals("Allowlist", ignoreCase = true) || name.equals("Whitelist", ignoreCase = true) -> return AppStrings.get(R.string.ui_6419e29c88)
        name.equals("Threat Intelligence Feeds", ignoreCase = true) -> return AppStrings.get(R.string.ui_2a58359877)
        name.equals("AI Threat Detection", ignoreCase = true) -> return AppStrings.get(R.string.ui_ee6d30b548)
        name.equals("Google Safe Browsing", ignoreCase = true) -> return AppStrings.get(R.string.ui_b6b6e376c7)
        name.equals("Cryptojacking Protection", ignoreCase = true) || name.equals("Cryptojacking", ignoreCase = true) -> return AppStrings.get(R.string.ui_5839f75751)
        name.equals("DNS Rebinding Protection", ignoreCase = true) || name.equals("DNS Rebinding", ignoreCase = true) -> return AppStrings.get(R.string.ui_705a8885b5)
        name.equals("IDN Homograph Attacks Protection", ignoreCase = true) -> return AppStrings.get(R.string.ui_9ba4a1d72e)
        name.equals("Typosquatting Protection", ignoreCase = true) -> return AppStrings.get(R.string.ui_361b4eb00a)
        name.equals("Newly Registered Domains (NRD)", ignoreCase = true) || name.equals("Newly Registered Domains", ignoreCase = true) -> return AppStrings.get(R.string.ui_091c6aed71)
        name.equals("Dynamic DNS (DDNS)", ignoreCase = true) || name.equals("Dynamic DNS Hostnames", ignoreCase = true) -> return AppStrings.get(R.string.ui_ad98b36293)
        name.equals("Parked Domains", ignoreCase = true) -> return AppStrings.get(R.string.ui_3cf825a2e2)
        name.equals("Child Sexual Abuse Material (CSAM)", ignoreCase = true) -> return AppStrings.get(R.string.ui_6e34ff30da)
        name.equals("SafeSearch", ignoreCase = true) -> return AppStrings.get(R.string.ui_ce261422e7)
        name.equals("YouTube Restricted Mode", ignoreCase = true) -> return AppStrings.get(R.string.ui_692c4c985a)
        name.startsWith("Native Tracking (", ignoreCase = true) -> {
          val brand = name.substringAfter("(").substringBefore(")")
          return AppStrings.get(R.string.native_brand, brand)
        }
        !name.startsWith("blocklist:", ignoreCase = true) && !name.startsWith("native:", ignoreCase = true) -> return name
      }
    }

    // 2. ID based lookups
    val cleanId = id.removePrefix("blocklist:").removePrefix("parentalcontrol:").trim()

    return when {
      cleanId == "denylist" || cleanId == "blacklist" -> AppStrings.get(R.string.ui_8aa4ccc961)
      cleanId == "allowlist" || cleanId == "whitelist" -> AppStrings.get(R.string.ui_6419e29c88)
      cleanId == "block-bypass" || cleanId == "bypass" -> AppStrings.get(R.string.ui_7396ea2940)
      cleanId == "disguised-trackers" || cleanId == "cname-flattening" -> AppStrings.get(R.string.ui_63f2dd13cc)
      cleanId == "nextdns-recommended" -> AppStrings.get(R.string.ui_890f81a670)
      cleanId == "adguard-dns-filter" || cleanId == "adguard-mobile-filter" -> "AdGuard DNS filter"
      cleanId == "oisd" || cleanId == "oisd-full" || cleanId == "oisd-basic" -> "oisd"
      cleanId == "easylist" -> "EasyList"
      cleanId == "easyprivacy" -> "EasyPrivacy"
      cleanId == "stevenblack" -> "Steven Black"
      cleanId == "fanboy-annoyance" -> "Fanboy's Annoyance"
      cleanId == "notracking" -> "NoTracking"
      cleanId == "1hosts-pro" -> "1Hosts (Pro)"
      cleanId == "1hosts-lite" -> "1Hosts (Lite)"
      cleanId == "1hosts-mini" -> "1Hosts (Mini)"
      cleanId == "doh-dot-vpn-tor" -> "DoH/DoT/VPN/TOR"
      cleanId.startsWith("native:") || cleanId.startsWith("native-") -> {
        val brand = cleanId.removePrefix("native:").removePrefix("native-").trim()
        val formattedBrand = when (brand.lowercase(Locale.ROOT)) {
          "apple" -> "Apple"
          "xiaomi" -> "Xiaomi"
          "samsung" -> "Samsung"
          "huawei" -> "Huawei"
          "windows" -> "Windows"
          "roku" -> "Roku"
          "sonos" -> "Sonos"
          "alexa" -> "Amazon Alexa"
          "lg" -> "LG"
          else -> brand.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        AppStrings.get(R.string.native_formatted_brand, formattedBrand)
      }
      cleanId == "threat-intelligence-feeds" || cleanId == "threat-intelligence" -> AppStrings.get(R.string.ui_2a58359877)
      cleanId == "ai-threat-detection" || cleanId == "ai-threat" -> AppStrings.get(R.string.ui_ee6d30b548)
      cleanId == "google-safe-browsing" || cleanId == "safebrowsing" -> AppStrings.get(R.string.ui_b6b6e376c7)
      cleanId == "cryptojacking" -> AppStrings.get(R.string.ui_5839f75751)
      cleanId == "dns-rebinding" -> AppStrings.get(R.string.ui_705a8885b5)
      cleanId == "idn-homographs" || cleanId == "homographs" -> AppStrings.get(R.string.ui_9ba4a1d72e)
      cleanId == "typosquatting" -> AppStrings.get(R.string.ui_361b4eb00a)
      cleanId == "dga" -> AppStrings.get(R.string.ui_7968337d16)
      cleanId == "nrd" -> AppStrings.get(R.string.ui_091c6aed71)
      cleanId == "ddns" -> AppStrings.get(R.string.ui_ad98b36293)
      cleanId == "parking" || cleanId == "parked-domains" -> AppStrings.get(R.string.ui_3cf825a2e2)
      cleanId == "csam" -> AppStrings.get(R.string.ui_6e34ff30da)
      cleanId == "safesearch" -> AppStrings.get(R.string.ui_ce261422e7)
      cleanId == "youtube-restricted-mode" || cleanId == "youtube-restricted" -> AppStrings.get(R.string.ui_692c4c985a)
      cleanId == "block-page" -> AppStrings.get(R.string.ui_dbea792aea)
      cleanId == "tlds" || cleanId == "blocked-tlds" -> AppStrings.get(R.string.ui_372431170f)
      name.isNotBlank() -> name
      preservedId.isNotBlank() -> preservedId
      else -> AppStrings.get(R.string.ui_6cceb67979)
    }
  }

  fun formatIsoDateWithRelative(rawTime: String?): String {
    if (rawTime.isNullOrBlank()) return ""
    val trimmed = rawTime.trim()

    try {
      val epochMillis: Long? = when {
        trimmed.toLongOrNull() != null -> {
          val num = trimmed.toLong()
          if (num < 10000000000L) num * 1000 else num
        }
        else -> {
          try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
              java.time.Instant.parse(trimmed).toEpochMilli()
            } else {
              val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT)
              sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
              sdf.parse(trimmed)?.time
            }
          } catch (_: Exception) {
            try {
              val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT)
              sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
              sdf.parse(trimmed)?.time
            } catch (_: Exception) {
              null
            }
          }
        }
      }

      if (epochMillis != null && epochMillis > 0) {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
          timeInMillis = epochMillis
        }
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val monthNames = java.text.DateFormatSymbols(AppStrings.locale).shortMonths
        val monthStr = monthNames.getOrElse(cal.get(java.util.Calendar.MONTH)) { "" }
        val year = cal.get(java.util.Calendar.YEAR)
        val formattedDate = "$day $monthStr $year"

        val now = System.currentTimeMillis()
        val diffMs = now - epochMillis

        val relative = if (diffMs <= 0) {
          AppStrings.get(R.string.ui_1279dae674)
        } else {
          val diffSec = diffMs / 1000
          val diffMin = diffSec / 60
          val diffHours = diffMin / 60
          val diffDays = diffHours / 24

          when {
            diffMin < 1 -> AppStrings.get(R.string.ui_ec3f106d56)
            diffMin < 60 -> AppStrings.plural(R.plurals.minutes_ago, diffMin)
            diffHours < 24 -> AppStrings.plural(R.plurals.hours_ago, diffHours)
            diffDays == 1L -> AppStrings.get(R.string.ui_2693187887)
            diffDays < 30 -> AppStrings.plural(R.plurals.days_ago, diffDays)
            diffDays < 365 -> AppStrings.plural(R.plurals.months_ago, diffDays / 30)
            else -> AppStrings.plural(R.plurals.years_ago, diffDays / 365)
          }
        }

        return "$formattedDate ($relative)"
      }
    } catch (_: Exception) {
      // ignore
    }

    return trimmed
  }

  fun determineBlocklistCategory(id: String, name: String, desc: String): String {
    val lower = "$id $name $desc".lowercase()
    return when {
      lower.contains("malware") || lower.contains("threat") || lower.contains("phishing") ||
        lower.contains("security") || lower.contains("güvenlik") || lower.contains("crypto") ||
        lower.contains("scam") || lower.contains("ransomware") || lower.contains("c2") -> AppStrings.get(R.string.ui_bde6632ed8)
      lower.contains("privacy") || lower.contains("gizlilik") || lower.contains("telemetry") ||
        lower.contains("tracker") || lower.contains("tracking") || lower.contains("izle") ||
        lower.contains("facebook") || lower.contains("google") || lower.contains("smarttv") -> "Gizlilik"
      lower.contains("turk") || lower.contains("french") || lower.contains("german") ||
        lower.contains("polish") || lower.contains("persian") || lower.contains("arabic") ||
        lower.contains("korean") || lower.contains("chinese") || lower.contains("japan") ||
        lower.contains("regional") || lower.contains("bölge") || lower.contains("russian") ||
        lower.contains("czech") || lower.contains("vietnam") || lower.contains("spanish") ||
        lower.contains("israel") || lower.contains("lithuania") || lower.contains("indonesia") ||
        lower.contains("swedish") || lower.contains("finnish") || lower.contains("dutch") -> AppStrings.get(R.string.ui_5ed0130b25)
      else -> "Genel"
    }
  }

  fun formatRelativeTime(rawTime: String?): String {
    return formatIsoDateWithRelative(rawTime)
  }
}

