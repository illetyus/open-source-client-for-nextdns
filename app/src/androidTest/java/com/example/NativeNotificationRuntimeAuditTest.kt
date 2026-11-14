package com.example

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.i18n.LocalePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Validates permission outcomes after the isolated CI emulator sets each runtime state. */
@RunWith(AndroidJUnit4::class)
class NativeNotificationRuntimeAuditTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Before fun setDeterministicLocale() {
    LocalePreferences.setSelection(context, "en")
  }

  @After fun clearNotifications() {
    context.getSystemService(NotificationManager::class.java).cancelAll()
  }

  @Test fun deniedPermissionPreventsNotifications() {
    NotificationRuntimeAssertions.assertDeniedPermissionPreventsNotifications(context)
  }

  @Test fun grantedPermissionPostsPrivateNotifications() {
    if (Build.VERSION.SDK_INT >= 33) {
      assertEquals(
        PackageManager.PERMISSION_GRANTED,
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
      )
    }
    NotificationRuntimeAssertions.assertPrivateNotificationsHaveGenericPublicVersions(context)
  }
}
