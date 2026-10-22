package com.example

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.notifications.NotificationCenter
import com.example.i18n.AppStrings
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeNotificationPermissionTest {
  private fun awaitCondition(message: String, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + 5_000
    while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
    assertTrue(message, condition())
  }

  @Test fun android13PermissionDenialPreventsPrivateNotifications() {
    assumeTrue("This permission scenario requires Android 13+", Build.VERSION.SDK_INT >= 33)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val manager = context.getSystemService(NotificationManager::class.java)
    assumeTrue(
      "The release audit must revoke notification permission before starting instrumentation",
      context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED
    )
    try {
      NotificationCenter.createChannels(context)
      manager.cancelAll()
      awaitCondition("Previous notifications were not cleared") { manager.activeNotifications.isEmpty() }
      assertNotNull(manager.getNotificationChannel(NotificationCenter.CHANNEL_CONFIG_CHANGES))
      assertNotNull(manager.getNotificationChannel(NotificationCenter.CHANNEL_DAILY_SUMMARY))
      assertFalse(NotificationCenter.canPost(context, NotificationCenter.CHANNEL_CONFIG_CHANGES))
      assertFalse(NotificationCenter.canPost(context, NotificationCenter.CHANNEL_DAILY_SUMMARY))
      assertFalse(NotificationCenter.postConfigChanged(context))
      assertFalse(NotificationCenter.postDailySummary(context, 9876543L, 1234567L))
      SystemClock.sleep(300)
      assertTrue("Denied permission must not emit either notification", manager.activeNotifications.isEmpty())
    } finally {
      manager.cancelAll()
    }
  }

  @Test fun grantedPermissionPostsPrivateNotificationsWithGenericPublicVersions() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val manager = context.getSystemService(NotificationManager::class.java)
    val needsPermission = Build.VERSION.SDK_INT >= 33
    assertTrue(
      "The release audit must grant notification permission before starting instrumentation",
      !needsPermission || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    )
    try {
      NotificationCenter.createChannels(context)
      manager.cancelAll()
      awaitCondition("Previous notifications were not cleared") { manager.activeNotifications.isEmpty() }
      assertTrue(NotificationCenter.canPost(context, NotificationCenter.CHANNEL_CONFIG_CHANGES))
      assertTrue(NotificationCenter.canPost(context, NotificationCenter.CHANNEL_DAILY_SUMMARY))
      assertTrue(NotificationCenter.postConfigChanged(context))
      assertTrue(NotificationCenter.postDailySummary(context, 9876543L, 1234567L))
      awaitCondition("Both notifications must actually reach Android") { manager.activeNotifications.size == 2 }
      val posted = manager.activeNotifications.map { it.notification }
      if (Build.VERSION.SDK_INT >= 26) {
        assertEquals(setOf(NotificationCenter.CHANNEL_CONFIG_CHANGES, NotificationCenter.CHANNEL_DAILY_SUMMARY), posted.map { it.channelId }.toSet())
      }
      for (notification in posted) {
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val publicVersion = notification.publicVersion
        assertNotNull("A generic lockscreen replacement is required", publicVersion)
        assertEquals(context.getString(R.string.app_name), publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(AppStrings.get(R.string.ui_092e2e8b4e), publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNull(publicVersion.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        val publicFields = publicVersion.extras.keySet().map { publicVersion.extras.get(it).toString() }
        assertTrue(publicFields.none { "9876543" in it || "1234567" in it })
        assertFalse(publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
      }
      val dailyText = AppStrings.get(R.string.ui_edf1c59922).format(AppStrings.locale, 9876543L, 1234567L)
      assertEquals(1, posted.count { it.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == dailyText })
    } finally {
      manager.cancelAll()
    }
  }
}
