package com.example.ui.viewmodel

import com.example.i18n.UiLabels

import com.example.R
import com.example.i18n.AppStrings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ApiConnectionStatus
import com.example.data.repository.NextDnsRepository
import com.example.data.repository.ParentalFlag
import com.example.data.repository.PrivacyFlag
import com.example.data.repository.SecurityFlag
import com.example.data.repository.SettingsPerformanceFlag
import com.example.data.repository.SectionSyncState
import com.example.data.repository.FullSyncStatus
import com.example.data.repository.SyncPolicy
import com.example.data.repository.SyncSection
import com.example.ui.theme.ThemeMode
import com.example.data.repository.exportToDocument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.UUID

enum class NavTab(val titleResource: Int, val iconName: String) {
  SETUP(R.string.ui_daee5e5093, "dns"),
  SECURITY(R.string.ui_bde6632ed8, "security"),
  PRIVACY(R.string.privacy, "visibility_off"),
  PARENTAL(R.string.ui_c8073d04ad, "family_restroom"),
  DENYLIST(R.string.ui_8aa4ccc961, "block"),
  ALLOWLIST(R.string.ui_6419e29c88, "check_circle"),
  ANALYTICS(R.string.ui_34db704cad, "insights"),
  LOGS(R.string.ui_a007acc25e, "format_list_bulleted"),
  SETTINGS(R.string.ui_80ad54ad05, "settings"),
  ACCOUNT(R.string.nav_connection, "account_circle");

  val title: String get() = AppStrings.get(titleResource)
}

private fun NavTab.toSyncSection(): SyncSection? = when (this) {
  NavTab.SETUP -> null
  NavTab.SECURITY -> SyncSection.SECURITY
  NavTab.PRIVACY -> SyncSection.PRIVACY
  NavTab.PARENTAL -> SyncSection.PARENTAL
  NavTab.DENYLIST -> SyncSection.DENYLIST
  NavTab.ALLOWLIST -> SyncSection.ALLOWLIST
  NavTab.SETTINGS -> SyncSection.SETTINGS
  NavTab.ACCOUNT, NavTab.ANALYTICS, NavTab.LOGS -> null
}

data class UiMessage(
  val id: Long = System.currentTimeMillis(),
  val text: String,
  val isError: Boolean = false
)

class NextDnsViewModel(
  private val repository: NextDnsRepository = NextDnsRepository()
) : ViewModel() {

  private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
  val themeMode = _themeMode.asStateFlow()
  
  fun setThemeMode(mode: ThemeMode) {
      _themeMode.value = mode
  }
  
  // Initialize theme mode from DataStore
  init {
      viewModelScope.launch {
          // This would ideally be passed in.
          // For now, let's keep it simple as requested
      }
  }

  val hasApiKey: StateFlow<Boolean> = repository.apiKey
    .map { it.isNotBlank() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.apiKey.value.isNotBlank())

  fun maskedApiKey(): String {
    val key = repository.apiKey.value
    if (key.isBlank()) return ""
    if (key.length <= 8) return "••••••••"
    return key.take(4) + "••••••••••••••••" + key.takeLast(4)
  }

  fun currentApiKeyForSensitiveUse(): String = repository.apiKey.value

  val apiStatus = repository.apiStatus
  val profiles = repository.profiles
  val activeProfileId = repository.activeProfileId
  val securitySettings = repository.securitySettings
  val unverifiedSecurityFields = repository.unverifiedSecurityFields
  val privacySettings = repository.privacySettings
  val parentalControlSettings = repository.parentalControlSettings
  val denylist = repository.denylist
  val allowlist = repository.allowlist
  val logs = repository.logs
  val logsPaginationState = repository.logsPaginationState
  val logsDeviceFilter = repository.logsDeviceFilter
  val logsStatusFilter = repository.logsStatusFilter
  val logsSearchFilter = repository.logsSearchFilter
  val logsHistoryFilter = repository.logsHistoryFilter
  val logDevices = repository.logDevices
  val analytics = repository.analytics
  val analyticsLastSuccessAt = repository.analyticsLastSuccessAt
  val analyticsErrorMessage = repository.analyticsErrorMessage
  val allKnownDevices = repository.allKnownDevices
  val configSettings = repository.configSettings
  val testResult = repository.testResult
  val isLiveStreaming = repository.isLiveStreaming
  val isSyncing = repository.isSyncing
  val sectionSyncStates = repository.sectionSyncStates
  private val _fullSyncStatus = MutableStateFlow<FullSyncStatus?>(null)
  val fullSyncStatus = _fullSyncStatus.asStateFlow()

  val availableBlocklistsCatalog = repository.availableBlocklistsCatalog
  val availableNativesCatalog = repository.availableNativesCatalog
  val availableParentalServicesCatalog = repository.availableParentalServicesCatalog
  val availableParentalCategoriesCatalog = repository.availableParentalCategoriesCatalog
  val availableTldsCatalog = repository.availableTldsCatalog
  val tldCatalogLoadState = repository.tldCatalogLoadState
  private val _isAnalyticsLoading = MutableStateFlow(false)
  val isAnalyticsLoading = _isAnalyticsLoading.asStateFlow()

  private val _isGuestMode = MutableStateFlow(false)
  val isGuestMode = _isGuestMode.asStateFlow()

  private val _isInitializing = MutableStateFlow(true)
  val isInitializing = _isInitializing.asStateFlow()

  val isLoggedIn: StateFlow<Boolean?> = combine(apiStatus, _isGuestMode) { status, isGuest ->
    when (status) {
      is ApiConnectionStatus.Connected -> {
        _isInitializing.value = false
        true
      }
      is ApiConnectionStatus.Disconnected -> {
        _isInitializing.value = false
        isGuest
      }
      is ApiConnectionStatus.Error -> {
        // A saved key may no longer be valid. Show login instead of trapping
        // the user behind the splash screen indefinitely.
        _isInitializing.value = false
        false
      }
      is ApiConnectionStatus.Connecting -> null
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val activeProfile: StateFlow<NextDnsProfile?> = combine(profiles, activeProfileId) { profs, id ->
    profs.find { it.id == id } ?: profs.firstOrNull()
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  private val _currentTab = MutableStateFlow(NavTab.SETUP)
  val currentTab = _currentTab.asStateFlow()

  val currentSectionSyncState: StateFlow<SectionSyncState?> =
    combine(currentTab, sectionSyncStates) { tab, states ->
      tab.toSyncSection()?.let { states[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  private var visibleSectionSyncJob: Job? = null
  private var foregroundProfileSyncJob: Job? = null
  private var authenticationJob: Job? = null
  private var analyticsRefreshJob: Job? = null
  private var analyticsRefreshGeneration = 0L

  private val _uiMessage = MutableStateFlow<UiMessage?>(null)
  val uiMessage = _uiMessage.asStateFlow()

  private val diagnosticMutex = Mutex()
  private val _isDiagnosticRunning = MutableStateFlow(false)
  val isDiagnosticRunning = _isDiagnosticRunning.asStateFlow()

  fun syncAllData() {
    viewModelScope.launch {
      if (isSyncing.value || _fullSyncStatus.value == FullSyncStatus.Running) return@launch
      val key = repository.apiKey.value
      val profileId = activeProfileId.value
      if (key.isBlank()) {
        _fullSyncStatus.value = FullSyncStatus.ApiKeyRequired
        showMessage(AppStrings.get(R.string.sync_api_key_required), isError = true)
        return@launch
      }
      if (profileId.isBlank()) {
        _fullSyncStatus.value = FullSyncStatus.ProfileRequired
        showMessage(AppStrings.get(R.string.sync_profile_required), isError = true)
        return@launch
      }

      _fullSyncStatus.value = FullSyncStatus.Running
      try {
        val result = repository.loadActiveProfileDataFromApi(key, profileId)
        if (result.sectionResults.isEmpty()) {
          _fullSyncStatus.value = FullSyncStatus.Failed
          showMessage(AppStrings.get(R.string.ui_4aae2f5c53), isError = true)
          return@launch
        }

        _fullSyncStatus.value = FullSyncStatus.Completed(result)
        if (result.allAreasSucceeded) {
          showMessage(AppStrings.get(R.string.sync_all_success))
        } else {
          val failedAreas = buildList {
            result.sectionResults.filterValues { !it }.keys.forEach { section ->
              add(NavTab.valueOf(section.name).title)
            }
            if (!result.logsRefreshed) add(NavTab.LOGS.title)
            if (!result.analyticsRefreshed) add(NavTab.ANALYTICS.title)
          }
          val labels = failedAreas.joinToString()
          showMessage(
            if (labels.isBlank()) AppStrings.get(R.string.ui_4aae2f5c53)
            else AppStrings.get(R.string.sync_failed_sections, labels),
            isError = true
          )
        }
      } catch (error: CancellationException) {
        _fullSyncStatus.value = FullSyncStatus.Failed
        throw error
      } catch (_: Exception) {
        _fullSyncStatus.value = FullSyncStatus.Failed
        showMessage(AppStrings.get(R.string.ui_4aae2f5c53), isError = true)
      }
    }
  }

  fun selectTab(tab: NavTab) {
    _currentTab.value = tab
  }

  fun startVisibleTabSync(tab: NavTab) {
    visibleSectionSyncJob?.cancel()
    val section = tab.toSyncSection() ?: return

    visibleSectionSyncJob = viewModelScope.launch {
      repository.refreshSection(section)

      val intervalMs: Long = when (tab) {
        NavTab.SETUP, NavTab.ACCOUNT -> return@launch
        else -> SyncPolicy.SECTION_POLL_MS
      }

      var nextDelayMs = intervalMs
      while (true) {
        delay(nextDelayMs)
        val success = repository.refreshSection(section)
        nextDelayMs = SyncPolicy.nextDelay(
          success = success,
          currentDelayMs = nextDelayMs,
          baseDelayMs = intervalMs,
          maxDelayMs = SyncPolicy.SECTION_MAX_BACKOFF_MS
        )
      }
    }
  }

  fun stopVisibleTabSync() {
    visibleSectionSyncJob?.cancel()
    visibleSectionSyncJob = null
  }

  fun startForegroundProfileSync() {
    foregroundProfileSyncJob?.cancel()
    foregroundProfileSyncJob = viewModelScope.launch {
      var nextDelayMs = SyncPolicy.PROFILE_POLL_MS

      repository.refreshProfilesFromApi()

      while (true) {
        delay(nextDelayMs)
        val success = repository.refreshProfilesFromApi()
        nextDelayMs = SyncPolicy.nextDelay(
          success = success,
          currentDelayMs = nextDelayMs,
          baseDelayMs = SyncPolicy.PROFILE_POLL_MS,
          maxDelayMs = SyncPolicy.PROFILE_MAX_BACKOFF_MS
        )
      }
    }
  }

  fun stopForegroundProfileSync() {
    foregroundProfileSyncJob?.cancel()
    foregroundProfileSyncJob = null
  }

  fun dismissMessage() {
    _uiMessage.value = null
  }

  fun showMessage(msg: String, isError: Boolean = false) {
    _uiMessage.value = UiMessage(text = msg, isError = isError)
  }

  private fun reportMutationResult(
    result: Result<Unit>,
    successMessage: String? = null
  ) {
    if (result.isSuccess) {
      if (!successMessage.isNullOrBlank()) showMessage(successMessage)
    } else {
      showMessage(
        result.exceptionOrNull()?.message ?: AppStrings.get(R.string.ui_fcd85e4b66),
        isError = true
      )
    }
  }

  fun saveApiKey(key: String) {
    loginWithApiKey(key)
  }

  fun resumeAfterTermsAccepted() {
    repository.resumeAfterTermsAccepted()
  }

  fun enterGuestMode() {
    continueAsGuest()
  }

  fun continueAsGuest() {
    if (!repository.termsAccepted()) return
    authenticationJob?.cancel()
    authenticationJob = null
    stopVisibleTabSync()
    stopForegroundProfileSync()
    repository.suspendForGuest()
    _isGuestMode.value = true
    showMessage(AppStrings.get(R.string.demo_started))
  }

  fun loginWithApiKey(key: String) {
    if (!repository.termsAccepted()) return
    authenticationJob?.cancel()
    authenticationJob = viewModelScope.launch {
      val result = repository.loginWithApiKey(key)
      if (result.isSuccess) {
        val count = result.getOrNull() ?: 1
        _isGuestMode.value = false
        showMessage(AppStrings.get(R.string.connected_profiles, count))
      } else {
        val err = result.exceptionOrNull()?.localizedMessage ?: AppStrings.get(R.string.ui_159dae4d54)
        showMessage(err, isError = true)
      }
    }
  }

  fun logout() {
    authenticationJob?.cancel()
    authenticationJob = null
    stopVisibleTabSync()
    stopForegroundProfileSync()
    _isGuestMode.value = false
    viewModelScope.launch {
      val result = repository.logout()
      if (result.isSuccess) {
        showMessage(AppStrings.get(R.string.ui_90f2c9060d))
      } else {
        showMessage(
          result.exceptionOrNull()?.message ?: AppStrings.get(R.string.ui_0a6b6417d9),
          isError = true
        )
      }
    }
  }

  fun switchProfile(profileId: String) {
    repository.setActiveProfile(profileId)
    showMessage(AppStrings.get(R.string.active_profile_changed, profileId))
  }

  fun createProfile(name: String) {
    viewModelScope.launch {
      val result = repository.createProfileRemote(name)
      if (result.isSuccess) {
        showMessage(AppStrings.get(R.string.ui_280a4e8b98))
      } else {
        showMessage(
          result.exceptionOrNull()?.message ?: AppStrings.get(R.string.ui_d730312319),
          isError = true
        )
      }
    }
  }

  fun deleteProfile(profileId: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.deleteProfileRemote(profileId),
        successMessage = AppStrings.get(R.string.ui_f9bd037a37)
      )
    }
  }

  fun renameProfile(newName: String) {
    activeProfile.value?.id?.let { pid ->
      viewModelScope.launch {
        reportMutationResult(
          repository.renameProfile(pid, newName),
          successMessage = AppStrings.get(R.string.profile_name_updated, newName)
        )
      }
    }
  }

  fun renameProfile(profileId: String, newName: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.renameProfile(profileId, newName),
        successMessage = AppStrings.get(R.string.ui_977287c21d)
      )
    }
  }

  // Security
  fun toggleSecurityFeature(feature: String, enabled: Boolean) {
    val flag = when (feature) {
      "threatIntelligenceFeeds" -> SecurityFlag.THREAT_INTELLIGENCE_FEEDS
      "aiThreatDetection" -> SecurityFlag.AI_THREAT_DETECTION
      "googleSafeBrowsing" -> SecurityFlag.GOOGLE_SAFE_BROWSING
      "cryptojacking" -> SecurityFlag.CRYPTOJACKING
      "dnsRebinding" -> SecurityFlag.DNS_REBINDING
      "idnHomographs" -> SecurityFlag.IDN_HOMOGRAPHS
      "typosquatting" -> SecurityFlag.TYPOSQUATTING
      "dga" -> SecurityFlag.DGA
      "nrd" -> SecurityFlag.NRD
      "ddns" -> SecurityFlag.DDNS
      "parkedDomains" -> SecurityFlag.PARKING
      "csam" -> SecurityFlag.CSAM
      else -> {
        showMessage(AppStrings.get(R.string.unknown_security_setting, feature), isError = true)
        return
      }
    }

    viewModelScope.launch {
      reportMutationResult(repository.setSecurityFlag(flag, enabled))
    }
  }

  fun addBlockedTld(tld: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.addBlockedTld(tld),
        successMessage = AppStrings.get(R.string.tld_blocked, tld)
      )
    }
  }

  fun removeBlockedTld(tld: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.removeBlockedTld(tld),
        successMessage = AppStrings.get(R.string.tld_unblocked, tld)
      )
    }
  }

  // Privacy
  fun toggleBlocklist(blocklistId: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.toggleBlocklist(blocklistId),
        successMessage = AppStrings.get(R.string.ui_e70a2c89d4)
      )
    }
  }

  fun toggleNativeTracking(nativeId: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.toggleNativeTracking(nativeId),
        successMessage = AppStrings.get(R.string.ui_2ba97d5430)
      )
    }
  }

  fun toggleDisguisedTrackers(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setPrivacyFlag(PrivacyFlag.DISGUISED_TRACKERS, enabled)
      )
    }
  }

  fun toggleAllowAffiliates(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setPrivacyFlag(PrivacyFlag.ALLOW_AFFILIATE_LINKS, enabled)
      )
    }
  }

  // Parental Control
  fun toggleParentalService(serviceId: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.toggleParentalService(serviceId),
        successMessage = AppStrings.get(R.string.ui_0c1f8969ba)
      )
    }
  }

  fun toggleParentalCategory(categoryId: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.toggleParentalCategory(categoryId),
        successMessage = AppStrings.get(R.string.ui_b9866d0f3d)
      )
    }
  }

  fun setSafeSearch(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setParentalFlag(ParentalFlag.SAFE_SEARCH, enabled)
      )
    }
  }

  fun setYoutubeRestricted(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setParentalFlag(ParentalFlag.YOUTUBE_RESTRICTED_MODE, enabled)
      )
    }
  }

  fun setBlockBypass(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setParentalFlag(ParentalFlag.BLOCK_BYPASS, enabled)
      )
    }
  }

  // Denylist
  fun addToDenylist(domain: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      val result = repository.addToDenylist(domain)
      reportMutationResult(result, successMessage = AppStrings.get(R.string.denylist_added, domain))
      if (result.isSuccess) onSuccess()
    }
  }

  fun removeFromDenylist(domain: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.removeFromDenylist(domain),
        successMessage = AppStrings.get(R.string.denylist_removed, domain)
      )
    }
  }

  fun toggleDenylistItem(domain: String) {
    viewModelScope.launch {
      reportMutationResult(repository.toggleDenylistItem(domain))
    }
  }

  // Allowlist
  fun addToAllowlist(domain: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      val result = repository.addToAllowlist(domain)
      reportMutationResult(result, successMessage = AppStrings.get(R.string.allowlist_added, domain))
      if (result.isSuccess) onSuccess()
    }
  }

  fun removeFromAllowlist(domain: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.removeFromAllowlist(domain),
        successMessage = AppStrings.get(R.string.allowlist_removed, domain)
      )
    }
  }

  fun toggleAllowlistItem(domain: String) {
    viewModelScope.launch {
      reportMutationResult(repository.toggleAllowlistItem(domain))
    }
  }

  // Logs & Live Stream
  fun toggleLiveStream() {
    val current = isLiveStreaming.value
    val newState = !current
    repository.setLiveStreaming(newState)
  }

  fun refreshLogs(showToast: Boolean = true) {
    viewModelScope.launch {
      val success = repository.refreshLogsFromApi()
      if (showToast) {
        if (success) {
          showMessage(AppStrings.get(R.string.ui_cf14e423f5))
        } else {
          showMessage(AppStrings.get(R.string.ui_494d4e0007), isError = true)
        }
      }
    }
  }

  fun loadMoreLogs() {
    viewModelScope.launch { repository.loadMoreLogsFromApi() }
  }

  fun setLogsDeviceFilter(deviceId: String?) {
    viewModelScope.launch { repository.setLogsDeviceFilter(deviceId) }
  }

  fun setLogsStatusFilter(status: String?) {
    viewModelScope.launch { repository.setLogsStatusFilter(status) }
  }

  fun setLogsSearchFilter(search: String?) {
    viewModelScope.launch { repository.setLogsSearchFilter(search) }
  }

  fun setLogsHistoryFilter(filter: LogsHistoryFilter) {
    viewModelScope.launch { repository.setLogsHistoryFilter(filter) }
  }

  fun refreshAnalytics(device: String?, time: String?) {
    val generation = ++analyticsRefreshGeneration
    analyticsRefreshJob?.cancel()
    _isAnalyticsLoading.value = true
    analyticsRefreshJob = viewModelScope.launch {
      try {
        repository.fetchAnalytics(repository.apiKey.value, activeProfileId.value, device, time)
      } finally {
        if (generation == analyticsRefreshGeneration) _isAnalyticsLoading.value = false
      }
    }
  }

  fun startAnalyticsPolling() {
      repository.startAnalyticsPolling()
  }

  fun stopAnalyticsPolling() {
      repository.stopAnalyticsPolling()
  }

  fun startLogsStream() {
      repository.startLogsStream()
  }

  fun stopLogsStream() {
      repository.stopLogsStream()
  }

  // Diagnostics
  fun runDiagnostic(showToast: Boolean = false) {
    viewModelScope.launch { refreshDiagnostic(showToast) }
  }

  suspend fun refreshDiagnostic(showToast: Boolean = false) = diagnosticMutex.withLock {
    _isDiagnosticRunning.value = true
    try {
      val activePid = repository.activeProfileId.value
      val result = repository.runDiagnosticTest(activePid.takeIf { it.isNotBlank() })
      if (showToast) {
        val state = diagnosticConnectionState(repository.profiles.value.find { it.id == activePid }, result)
        val matched = state == DiagnosticConnectionState.MATCHED_PROFILE
        val message = when (state) {
          DiagnosticConnectionState.MATCHED_PROFILE -> {
            val details = buildList {
              if (result.latencyMs > 0) add("${result.latencyMs} ms")
              result.protocol.takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(" • ")
            if (details.isBlank()) AppStrings.get(R.string.ui_eb367c1e14)
            else AppStrings.get(R.string.protection_details, details)
          }
          DiagnosticConnectionState.UNVERIFIED_PROFILE -> AppStrings.get(R.string.diagnostic_unverified_title)
          DiagnosticConnectionState.NO_SELECTED_PROFILE -> AppStrings.get(R.string.diagnostic_detected_title)
          DiagnosticConnectionState.ERROR -> AppStrings.get(R.string.ui_19b9ff3444)
          else -> AppStrings.get(R.string.ui_cab8dbe1ad)
        }
        showMessage(message, isError = !matched && state != DiagnosticConnectionState.NO_SELECTED_PROFILE)
      }
    } finally {
      _isDiagnosticRunning.value = false
    }
  }

  // Settings
  fun toggleLogsEnabled(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(repository.setLogsEnabled(enabled))
    }
  }

  fun toggleLogClientIps(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(repository.setLogClientIps(enabled))
    }
  }

  fun toggleLogDomains(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(repository.setLogDomains(enabled))
    }
  }

  fun setLogRetention(retention: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setLogRetention(retention),
        successMessage = AppStrings.get(R.string.retention_updated, UiLabels.canonical(retention))
      )
    }
  }

  fun setLogStorageLocation(location: String) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setLogStorageLocation(location),
        successMessage = AppStrings.get(R.string.location_updated, UiLabels.canonical(location))
      )
    }
  }

  suspend fun exportLogs(outputStream: OutputStream): Result<Unit> {
    val result = repository.exportLogs(outputStream)
    reportMutationResult(
      result,
      successMessage = AppStrings.get(R.string.ui_a5562d8e36)
    )
    return result
  }

  suspend fun exportLogsToDocument(openOutput: () -> OutputStream?): Result<Unit> {
    val written = exportToDocument(openOutput) { repository.exportLogs(it) }
    val result = written.fold(onSuccess = { Result.success(Unit) }, onFailure = {
      Result.failure(IllegalStateException(AppStrings.get(R.string.ui_0813aca08b), it))
    })
    reportMutationResult(result, successMessage = AppStrings.get(R.string.ui_a5562d8e36))
    return result
  }

  fun clearLogs() {
    viewModelScope.launch {
      reportMutationResult(
        repository.clearLogs(),
        successMessage = AppStrings.get(R.string.ui_5324f7201f)
      )
    }
  }

  fun toggleBlockPage(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(repository.setBlockPage(enabled))
    }
  }

  fun toggleEdns(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setPerformanceFlag(SettingsPerformanceFlag.ECS, enabled)
      )
    }
  }

  fun toggleCacheBoost(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setPerformanceFlag(SettingsPerformanceFlag.CACHE_BOOST, enabled)
      )
    }
  }

  fun toggleCnameFlattening(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(
        repository.setPerformanceFlag(SettingsPerformanceFlag.CNAME_FLATTENING, enabled)
      )
    }
  }

  fun toggleWeb3(enabled: Boolean) {
    viewModelScope.launch {
      reportMutationResult(repository.setWeb3(enabled))
    }
  }

  override fun onCleared() {
    authenticationJob?.cancel()
    stopVisibleTabSync()
    stopForegroundProfileSync()
    super.onCleared()
  }
}
