package com.example.data.notifications

import android.content.Context
import com.example.data.legal.LegalAcceptanceStore
import com.example.data.local.NextDnsPreferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object NotificationWorkScheduler {
  private const val PERIODIC_WORK_NAME = "nextdns_notification_monitor"
  private const val IMMEDIATE_WORK_NAME = "nextdns_notification_monitor_now"

  suspend fun reconcile(
    context: Context,
    runImmediately: Boolean = false,
    baselineConfig: Boolean = false
  ) {
    val appContext = context.applicationContext
    val preferences = NotificationPreferences(appContext)
    val settings = preferences.currentSettings()
    val workManager = WorkManager.getInstance(appContext)

    if (!LegalAcceptanceStore(appContext).isAccepted() || NextDnsPreferences.getInstance(appContext).isGuestMode) {
      workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
      workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
      return
    }

    if (!settings.anyEnabled) {
      workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
      workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
      return
    }

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .setRequiresBatteryNotLow(true)
      .build()

    val periodic = PeriodicWorkRequestBuilder<NextDnsNotificationWorker>(
      NotificationPolicy.PERIODIC_INTERVAL_MINUTES,
      TimeUnit.MINUTES
    )
      .setConstraints(constraints)
      .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        10,
        TimeUnit.MINUTES
      )
      .build()

    workManager.enqueueUniquePeriodicWork(
      PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      periodic
    )

    if (runImmediately) {
      val immediate = OneTimeWorkRequestBuilder<NextDnsNotificationWorker>()
        .setInputData(
          workDataOf(
            NextDnsNotificationWorker.KEY_BASELINE_ONLY to baselineConfig
          )
        )
        .setConstraints(constraints)
        .setBackoffCriteria(
          BackoffPolicy.EXPONENTIAL,
          10,
          TimeUnit.MINUTES
        )
        .build()

      workManager.enqueueUniqueWork(
        IMMEDIATE_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        immediate
      )
    }
  }

  fun cancel(context: Context) {
    val workManager = WorkManager.getInstance(context.applicationContext)
    workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
    workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
  }

  suspend fun beginLocalConfigMutation(
    context: Context,
    profileId: String,
    now: Long = System.currentTimeMillis()
  ): Boolean {
    val appContext = context.applicationContext
    if (!LegalAcceptanceStore(appContext).isAccepted()) return false
    val preferences = NotificationPreferences(appContext)
    val settings = preferences.currentSettings()
    if (!settings.configChangeAlertsEnabled) return false

    preferences.suppressConfigForLocalMutation(
      profileId = profileId,
      until = NotificationPolicy.localMutationSuppressionUntil(now)
    )
    return true
  }

  suspend fun abortLocalConfigMutation(
    context: Context,
    profileId: String
  ) {
    NotificationPreferences(context.applicationContext)
      .clearLocalMutationSuppression(profileId)
  }

  suspend fun refreshConfigBaselineIfEnabled(context: Context) {
    val appContext = context.applicationContext
    if (!LegalAcceptanceStore(appContext).isAccepted()) return
    val settings = NotificationPreferences(appContext).currentSettings()
    if (!settings.configChangeAlertsEnabled) return

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .setRequiresBatteryNotLow(true)
      .build()

    val request = OneTimeWorkRequestBuilder<NextDnsNotificationWorker>()
      .setInputData(
        workDataOf(
          NextDnsNotificationWorker.KEY_BASELINE_ONLY to true
        )
      )
      .setConstraints(constraints)
      .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        10,
        TimeUnit.MINUTES
      )
      .build()

    WorkManager.getInstance(appContext).enqueueUniqueWork(
      IMMEDIATE_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request
    )
  }
}
