package com.example.data.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.i18n.AppStrings

object NotificationCenter {
  const val CHANNEL_CONFIG_CHANGES = "nextdns_config_changes"
  const val CHANNEL_DAILY_SUMMARY = "nextdns_daily_summary"

  internal const val NOTIFICATION_CONFIG_CHANGED = 2101
  internal const val NOTIFICATION_DAILY_SUMMARY = 2102

  fun createChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = context.getSystemService(NotificationManager::class.java)

    val changeChannel = NotificationChannel(
      CHANNEL_CONFIG_CHANGES,
      AppStrings.get(R.string.ui_d2a366ccf5),
      NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
      description = AppStrings.get(R.string.ui_50bdef725a)
    }

    val summaryChannel = NotificationChannel(
      CHANNEL_DAILY_SUMMARY,
      AppStrings.get(R.string.ui_bb763d7bf3),
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = AppStrings.get(R.string.ui_25243a32a1)
    }

    // Channel lockscreen visibility is controlled by Android/user settings.
    // The application sets PRIVATE and a generic publicVersion on each notification.
    manager.createNotificationChannels(listOf(changeChannel, summaryChannel))
  }

  fun canPost(context: Context, channelId: String): Boolean {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      return false
    }

    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
      return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(NotificationManager::class.java)
      val channel = manager.getNotificationChannel(channelId)
      if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
        return false
      }
    }

    return true
  }

  fun postConfigChanged(context: Context): Boolean {
    if (!canPost(context, CHANNEL_CONFIG_CHANGES)) return false

    val notification = baseBuilder(
      context = context,
      channelId = CHANNEL_CONFIG_CHANGES,
      title = AppStrings.get(R.string.ui_47afbf1118),
      text = AppStrings.get(R.string.ui_8273173721),
      priority = NotificationCompat.PRIORITY_DEFAULT
    ).build()

    return notifySafely(
      context = context,
      notificationId = NOTIFICATION_CONFIG_CHANGED,
      notification = notification
    )
  }

  fun postDailySummary(
    context: Context,
    totalQueries: Long,
    blockedQueries: Long
  ): Boolean {
    if (!canPost(context, CHANNEL_DAILY_SUMMARY)) return false

    val text = AppStrings.get(R.string.ui_edf1c59922)
      .format(AppStrings.locale, totalQueries, blockedQueries)

    val notification = baseBuilder(
      context = context,
      channelId = CHANNEL_DAILY_SUMMARY,
      title = AppStrings.get(R.string.ui_f88f548670),
      text = text,
      priority = NotificationCompat.PRIORITY_LOW
    ).build()

    return notifySafely(
      context = context,
      notificationId = NOTIFICATION_DAILY_SUMMARY,
      notification = notification
    )
  }

  private fun notifySafely(
    context: Context,
    notificationId: Int,
    notification: Notification
  ): Boolean {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      return false
    }

    return try {
      NotificationManagerCompat.from(context)
        .notify(notificationId, notification)
      true
    } catch (_: SecurityException) {
      false
    }
  }

  private fun baseBuilder(
    context: Context,
    channelId: String,
    title: String,
    text: String,
    priority: Int
  ): NotificationCompat.Builder {
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val publicVersion = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(R.drawable.ic_notification_dns)
      .setContentTitle(context.getString(R.string.app_name))
      .setContentText(AppStrings.get(R.string.ui_092e2e8b4e))
      .setPriority(priority)
      .build()

    return NotificationCompat.Builder(context, channelId)
      .setSmallIcon(R.drawable.ic_notification_dns)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .setOnlyAlertOnce(true)
      .setPriority(priority)
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setPublicVersion(publicVersion)
  }
}
