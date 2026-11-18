package com.example

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.example.data.notifications.NotificationCenter
import com.example.i18n.AppStrings
import org.junit.Assert.*

/** Assertions shared by the platform-prompt journey and deterministic runtime-permission audit. */
internal object NotificationRuntimeAssertions {
  fun assertDeniedPermissionPreventsNotifications(context: Context) {
    if (Build.VERSION.SDK_INT < 33) return

    assertEquals(
      PackageManager.PERMISSION_DENIED,
      context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
    )
    val manager = context.getSystemService(NotificationManager::class.java)
    NotificationCenter.createChannels(context)
    manager.cancelAll()
    assertFalse(NotificationCenter.canPost(context, NotificationCenter.CHANNEL_CONFIG_CHANGES))
    assertFalse(NotificationCenter.canPost(context, NotificationCenter.CHANNEL_DAILY_SUMMARY))
    assertFalse(NotificationCenter.postConfigChanged(context))
    assertFalse(NotificationCenter.postDailySummary(context, 9876543L, 1234567L))
    assertTrue("Denied permission must not emit either notification", manager.activeNotifications.isEmpty())
  }

  fun assertPrivateNotificationsHaveGenericPublicVersions(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    NotificationCenter.createChannels(context)
    manager.cancelAll()
    assertTrue(NotificationCenter.canPost(context, NotificationCenter.CHANNEL_CONFIG_CHANGES))
    assertTrue(NotificationCenter.canPost(context, NotificationCenter.CHANNEL_DAILY_SUMMARY))
    assertTrue(NotificationCenter.postConfigChanged(context))
    assertTrue(NotificationCenter.postDailySummary(context, 9876543L, 1234567L))

    val expectedIds = setOf(
      NotificationCenter.NOTIFICATION_CONFIG_CHANGED,
      NotificationCenter.NOTIFICATION_DAILY_SUMMARY
    )
    val deadline = SystemClock.uptimeMillis() + 5_000L
    while (SystemClock.uptimeMillis() < deadline &&
      !manager.activeNotifications.map { it.id }.containsAll(expectedIds)
    ) {
      SystemClock.sleep(50L)
    }
    val delivered = manager.activeNotifications.filter { it.id in expectedIds }
    assertEquals(expectedIds, delivered.map { it.id }.toSet())
    val posted = delivered.map { it.notification }
    if (Build.VERSION.SDK_INT >= 26) {
      assertEquals(
        setOf(NotificationCenter.CHANNEL_CONFIG_CHANGES, NotificationCenter.CHANNEL_DAILY_SUMMARY),
        posted.map { it.channelId }.toSet()
      )
    }
    for (notification in posted) {
      assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
      val publicVersion = requireNotNull(notification.publicVersion) {
        "A generic lockscreen replacement is required"
      }
      assertEquals(
        context.getString(R.string.app_name),
        publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
      )
      assertEquals(
        AppStrings.get(R.string.ui_092e2e8b4e),
        publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
      )
      assertNull(publicVersion.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
      val publicFields = publicVersion.extras.keySet().map { publicVersion.extras.get(it).toString() }
      assertTrue(publicFields.none { "9876543" in it || "1234567" in it })
      assertFalse(
        publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString() ==
          notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
      )
    }
    val dailyText = AppStrings.get(R.string.ui_edf1c59922)
      .format(AppStrings.locale, 9876543L, 1234567L)
    assertEquals(1, posted.count { it.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == dailyText })
  }
}
