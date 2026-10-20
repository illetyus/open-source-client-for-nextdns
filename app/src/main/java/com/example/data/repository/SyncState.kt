package com.example.data.repository

/** Remote profile sections with supported NextDNS endpoints; credentials and connection diagnostics stay local. */
enum class SyncSection {
  SETUP,
  SECURITY,
  PRIVACY,
  PARENTAL,
  DENYLIST,
  ALLOWLIST,
  SETTINGS
}

data class SectionSyncState(
  val isRefreshing: Boolean = false,
  val isSaving: Boolean = false,
  val lastAttemptAt: Long? = null,
  val lastSuccessAt: Long? = null,
  val errorMessage: String? = null
)

data class LogsPaginationState(
  val hasMore: Boolean = false,
  val isLoading: Boolean = false,
  val error: String? = null,
  val retentionLimitReached: Boolean = false
)

/** Results for supported profile data areas. Local credentials and connection diagnostics are excluded. */
data class ProfileDataSyncResult(
  val sectionResults: Map<SyncSection, Boolean>,
  val logsRefreshed: Boolean,
  val analyticsRefreshed: Boolean
) {
  val successfulAreaCount: Int
    get() = sectionResults.count { it.value } + (if (logsRefreshed) 1 else 0) + (if (analyticsRefreshed) 1 else 0)

  val totalAreaCount: Int
    get() = sectionResults.size + 2

  val allAreasSucceeded: Boolean
    get() = sectionResults.keys == SyncSection.values().toSet() &&
      sectionResults.values.all { it } && logsRefreshed && analyticsRefreshed
}

sealed interface FullSyncStatus {
  data object Running : FullSyncStatus
  data object ApiKeyRequired : FullSyncStatus
  data object ProfileRequired : FullSyncStatus
  data object Failed : FullSyncStatus
  data class Completed(val result: ProfileDataSyncResult) : FullSyncStatus
}
