package com.example.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.api.AllowDenyRuleDto
import com.example.data.api.NextDnsApiResponse
import com.example.data.api.NextDnsNetworkClient
import com.example.data.api.hasApiErrors
import com.example.data.local.NextDnsPreferences
import com.example.data.legal.LegalAcceptanceStore
import retrofit2.Response

class NextDnsNotificationWorker(
  appContext: Context,
  params: WorkerParameters
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    if (!LegalAcceptanceStore(applicationContext).isAccepted()) return Result.success()
    val accountGeneration = NotificationCenter.captureAccountGeneration() ?: return Result.success()

    val preferences = NotificationPreferences.forAccount(applicationContext, accountGeneration)
    val settings = runCatching {
      preferences.currentSettings()
    }.getOrElse {
      return Result.retry()
    }

    if (!settings.anyEnabled) return Result.success()

    val securePreferences = NextDnsPreferences.getInstance(applicationContext)
    if (securePreferences.isGuestMode) return Result.success()
    val apiKey = securePreferences.apiKey
    val profileId = securePreferences.activeProfileId

    if (apiKey.isBlank() || profileId.isBlank() ||
      !isCurrentSession(apiKey, profileId, accountGeneration)
    ) {
      return Result.success()
    }

    NotificationCenter.createChannels(applicationContext)

    val baselineOnly = inputData.getBoolean(KEY_BASELINE_ONLY, false)
    if (baselineOnly) {
      if (!settings.configChangeAlertsEnabled) return Result.success()

      val snapshot = fetchConfigSnapshot(apiKey, profileId)
        ?: return Result.retry()
      if (!isCurrentSession(apiKey, profileId, accountGeneration)) return Result.success()

      preferences.establishConfigBaseline(
        profileId = profileId,
        digest = ConfigSnapshotHasher.digest(snapshot)
      )
      return Result.success()
    }

    val canPostConfig =
      settings.configChangeAlertsEnabled &&
        NotificationCenter.canPost(
          applicationContext,
          NotificationCenter.CHANNEL_CONFIG_CHANGES
        )
    val canPostSummary =
      settings.dailySummaryEnabled &&
        NotificationCenter.canPost(
          applicationContext,
          NotificationCenter.CHANNEL_DAILY_SUMMARY
        )

    if (!canPostConfig && !canPostSummary) {
      return Result.success()
    }

    var shouldRetry = false
    val now = System.currentTimeMillis()

    if (canPostConfig) {
      val snapshot = fetchConfigSnapshot(apiKey, profileId)
      if (snapshot == null) {
        shouldRetry = true
      } else {
        if (!isCurrentSession(apiKey, profileId, accountGeneration)) return Result.success()
        handleConfigSnapshot(
          preferences = preferences,
          apiKey = apiKey,
          profileId = profileId,
          accountGeneration = accountGeneration,
          digest = ConfigSnapshotHasher.digest(snapshot),
          now = now
        )
      }
    }

    if (canPostSummary) {
      val today = NotificationPolicy.localDayKey(now)
      val lastSummaryDay = runCatching {
        preferences.lastSummaryDay()
      }.getOrNull()

      if (lastSummaryDay != today) {
        val summary = fetchDailySummary(apiKey, profileId)
        if (summary == null) {
          shouldRetry = true
        } else {
          if (!isCurrentSession(apiKey, profileId, accountGeneration)) return Result.success()
          val posted = NotificationCenter.postDailySummaryForAccount(
            context = applicationContext,
            totalQueries = summary.totalQueries,
            blockedQueries = summary.blockedQueries,
            generation = accountGeneration,
            isCurrentAccount = { isCurrentSession(apiKey, profileId, accountGeneration) }
          )

          if (posted && isCurrentSession(apiKey, profileId, accountGeneration)) {
            preferences.markSummaryDay(today)
          }
        }
      }
    }

    return if (shouldRetry) Result.retry() else Result.success()
  }

  private fun isCurrentSession(key: String, profileId: String, accountGeneration: Long): Boolean {
    if (!NotificationCenter.isAccountGenerationCurrent(accountGeneration)) return false
    if (!LegalAcceptanceStore(applicationContext).isAccepted()) return false
    val stored = NextDnsPreferences.getInstance(applicationContext)
    return !stored.isGuestMode && stored.apiKey == key && stored.activeProfileId == profileId
  }

  private suspend fun handleConfigSnapshot(
    preferences: NotificationPreferences,
    apiKey: String,
    profileId: String,
    accountGeneration: Long,
    digest: String,
    now: Long
  ) {
    val state = preferences.configState(profileId)
    // An async request may finish after sign-out or after account switching.
    if (!isCurrentSession(apiKey, profileId, accountGeneration)) return

    if (
      NotificationPolicy.isLocalMutationSuppressed(
        suppressUntil = state.localMutationSuppressUntil,
        now = now
      )
    ) {
      // The first observation after an app-originated mutation becomes the
      // new baseline. This prevents a delayed baseline worker from turning
      // our own successful write into a later "external change" alert.
      preferences.suppressConfigBacklog(profileId, digest)
      preferences.clearLocalMutationSuppression(profileId)
      return
    }

    if (state.localMutationSuppressUntil > 0L) {
      preferences.clearLocalMutationSuppression(profileId)
    }

    if (state.observedDigest == null) {
      preferences.establishConfigBaseline(profileId, digest)
      return
    }

    if (state.observedDigest != digest) {
      preferences.markConfigObserved(profileId, digest)
    }

    if (state.lastNotifiedDigest == digest) return

    if (!NotificationCenter.canPost(
        applicationContext,
        NotificationCenter.CHANNEL_CONFIG_CHANGES
      )
    ) {
      preferences.suppressConfigBacklog(profileId, digest)
      return
    }

    if (!isCurrentSession(apiKey, profileId, accountGeneration)) return

    if (
      NotificationPolicy.shouldNotifyConfigChange(
        currentDigest = digest,
        lastNotifiedDigest = state.lastNotifiedDigest,
        lastNotifiedAt = state.lastNotifiedAt,
        now = now
      ) &&
      NotificationCenter.postConfigChangedForAccount(
        applicationContext,
        accountGeneration,
        isCurrentAccount = { isCurrentSession(apiKey, profileId, accountGeneration) }
      )
    ) {
      if (isCurrentSession(apiKey, profileId, accountGeneration)) {
        preferences.markConfigNotified(profileId, digest, now)
      }
    }
  }

  private suspend fun fetchConfigSnapshot(
    apiKey: String,
    profileId: String
  ): NotificationConfigSnapshot? = runCatching {
    val api = NextDnsNetworkClient.api

    val security = api.getSecurity(apiKey, profileId)
      .requireDataOrNull() ?: return null

    val privacy = api.getPrivacy(apiKey, profileId)
      .requireDataOrNull() ?: return null

    val parental = api.getParentalControl(apiKey, profileId)
      .requireDataOrNull() ?: return null

    val settings = api.getSettings(apiKey, profileId)
      .requireDataOrNull() ?: return null

    val denylist = fetchAllRules { cursor ->
      api.getDenylist(
        apiKey = apiKey,
        profileId = profileId,
        limit = 100,
        cursor = cursor
      )
    } ?: return null

    val allowlist = fetchAllRules { cursor ->
      api.getAllowlist(
        apiKey = apiKey,
        profileId = profileId,
        limit = 100,
        cursor = cursor
      )
    } ?: return null

    NotificationConfigSnapshot(
      security = security,
      privacy = privacy,
      parental = parental,
      settings = settings,
      denylist = denylist,
      allowlist = allowlist
    )
  }.getOrNull()

  private suspend fun fetchAllRules(
    request: suspend (cursor: String?) -> Response<NextDnsApiResponse<List<AllowDenyRuleDto>>>
  ): List<AllowDenyRuleDto>? {
    val items = mutableListOf<AllowDenyRuleDto>()
    val seenCursors = mutableSetOf<String>()
    var cursor: String? = null

    do {
      val response = runCatching {
        request(cursor)
      }.getOrNull() ?: return null

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

    return items.distinctBy { it.id }
  }

  private suspend fun fetchDailySummary(
    apiKey: String,
    profileId: String
  ): DailySummary? = runCatching {
    val response = NextDnsNetworkClient.api.getAnalyticsStatus(
      apiKey = apiKey,
      profileId = profileId,
      from = "-1d",
      to = "now"
    )

    val body = response.body() ?: return null
    if (!response.isSuccessful || body.hasApiErrors()) return null

    val items = body.data.orEmpty()
    DailySummary(
      totalQueries = items.sumOf { it.queries ?: 0L },
      blockedQueries = items
        .filter { it.status.equals("blocked", ignoreCase = true) }
        .sumOf { it.queries ?: 0L }
    )
  }.getOrNull()

  private fun <T> Response<NextDnsApiResponse<T>>.requireDataOrNull(): T? {
    val body = body() ?: return null
    if (!isSuccessful || body.hasApiErrors()) return null
    return body.data
  }

  private data class DailySummary(
    val totalQueries: Long,
    val blockedQueries: Long
  )

  companion object {
    const val KEY_BASELINE_ONLY = "baseline_only"
  }
}
