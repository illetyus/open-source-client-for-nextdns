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

sealed interface FullSyncStatus {
  data object Running : FullSyncStatus
  data object ApiKeyRequired : FullSyncStatus
  data object ProfileRequired : FullSyncStatus
  data object Failed : FullSyncStatus
  data class Completed(val sectionResults: Map<SyncSection, Boolean>) : FullSyncStatus
}
