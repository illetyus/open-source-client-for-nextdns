package com.example.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "notification_settings"
)

data class NotificationSettings(
  val configChangeAlertsEnabled: Boolean = false,
  val dailySummaryEnabled: Boolean = false
) {
  val anyEnabled: Boolean
    get() = configChangeAlertsEnabled || dailySummaryEnabled
}

data class ConfigNotificationState(
  val observedDigest: String? = null,
  val lastNotifiedDigest: String? = null,
  val lastNotifiedAt: Long = 0L,
  val localMutationSuppressUntil: Long = 0L
)

class NotificationPreferences private constructor(context: Context, private val accountGeneration: Long?) {
  constructor(context: Context) : this(context, null)

  private val appContext = context.applicationContext
  private val dataStore = appContext.notificationDataStore

  private suspend fun editNotificationState(update: (MutablePreferences) -> Unit) {
    dataStore.edit { prefs ->
      val generation = accountGeneration
      if (generation == null) update(prefs)
      else NotificationCenter.updateCurrentAccountState(generation) { update(prefs) }
    }
  }

  val settings: Flow<NotificationSettings> = dataStore.data.map { prefs ->
    NotificationSettings(
      configChangeAlertsEnabled = prefs[CONFIG_ALERTS] ?: false,
      dailySummaryEnabled = prefs[DAILY_SUMMARY] ?: false
    )
  }

  suspend fun currentSettings(): NotificationSettings = settings.first()

  /** Remove notification toggles, per-profile digests, timestamps and baselines. */
  suspend fun clearAccountState() {
    dataStore.edit { prefs -> prefs.clear() }
  }

  suspend fun setConfigChangeAlertsEnabled(enabled: Boolean) {
    dataStore.edit { prefs ->
      prefs[CONFIG_ALERTS] = enabled
    }
  }

  suspend fun setDailySummaryEnabled(
    enabled: Boolean,
    now: Long = System.currentTimeMillis()
  ) {
    dataStore.edit { prefs ->
      val wasEnabled = prefs[DAILY_SUMMARY] ?: false
      prefs[DAILY_SUMMARY] = enabled

      // Enabling daily summary establishes today's baseline so the user
      // doesn't receive an immediate "daily" notification seconds later.
      if (enabled && !wasEnabled) {
        prefs[LAST_SUMMARY_DAY] = NotificationPolicy.localDayKey(now)
      }
    }
  }

  suspend fun configState(profileId: String): ConfigNotificationState {
    val suffix = NotificationPolicy.profileStorageSuffix(profileId)
    val prefs = dataStore.data.first()
    return ConfigNotificationState(
      observedDigest = prefs[stringPreferencesKey("config_observed_$suffix")],
      lastNotifiedDigest = prefs[stringPreferencesKey("config_notified_$suffix")],
      lastNotifiedAt = prefs[longPreferencesKey("config_notified_at_$suffix")] ?: 0L,
      localMutationSuppressUntil =
        prefs[longPreferencesKey("config_local_mutation_until_$suffix")] ?: 0L
    )
  }

  suspend fun establishConfigBaseline(profileId: String, digest: String) {
    val suffix = NotificationPolicy.profileStorageSuffix(profileId)
    editNotificationState { prefs ->
      prefs[stringPreferencesKey("config_observed_$suffix")] = digest
      prefs[stringPreferencesKey("config_notified_$suffix")] = digest
      prefs[longPreferencesKey("config_notified_at_$suffix")] = 0L
      prefs.remove(longPreferencesKey("config_local_mutation_until_$suffix"))
    }
  }

  suspend fun suppressConfigForLocalMutation(
    profileId: String,
    until: Long
  ) {
    val suffix = NotificationPolicy.profileStorageSuffix(profileId)
    dataStore.edit { prefs ->
      prefs[longPreferencesKey("config_local_mutation_until_$suffix")] = until
    }
  }

  suspend fun clearLocalMutationSuppression(profileId: String) {
    val suffix = NotificationPolicy.profileStorageSuffix(profileId)
    editNotificationState { prefs ->
      prefs.remove(longPreferencesKey("config_local_mutation_until_$suffix"))
    }
  }

  suspend fun markConfigObserved(profileId: String, digest: String) {
    val suffix = NotificationPolicy.profileStorageSuffix(profileId)
    editNotificationState { prefs ->
      prefs[stringPreferencesKey("config_observed_$suffix")] = digest
    }
  }

  suspend fun markConfigNotified(
    profileId: String,
    digest: String,
    now: Long
  ) {
    val suffix = NotificationPolicy.profileStorageSuffix(profileId)
    editNotificationState { prefs ->
      prefs[stringPreferencesKey("config_notified_$suffix")] = digest
      prefs[longPreferencesKey("config_notified_at_$suffix")] = now
    }
  }

  suspend fun suppressConfigBacklog(profileId: String, digest: String) {
    val suffix = NotificationPolicy.profileStorageSuffix(profileId)
    editNotificationState { prefs ->
      prefs[stringPreferencesKey("config_observed_$suffix")] = digest
      prefs[stringPreferencesKey("config_notified_$suffix")] = digest
    }
  }

  suspend fun lastSummaryDay(): String? =
    dataStore.data.first()[LAST_SUMMARY_DAY]

  suspend fun markSummaryDay(dayKey: String) {
    editNotificationState { prefs ->
      prefs[LAST_SUMMARY_DAY] = dayKey
    }
  }

  companion object {
    internal fun forAccount(context: Context, generation: Long): NotificationPreferences =
      NotificationPreferences(context, generation)

    private val CONFIG_ALERTS = booleanPreferencesKey("config_change_alerts")
    private val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
    private val LAST_SUMMARY_DAY = stringPreferencesKey("last_summary_day")
  }
}
