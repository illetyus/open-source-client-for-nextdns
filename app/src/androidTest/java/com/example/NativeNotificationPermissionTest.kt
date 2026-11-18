package com.example

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.data.legal.LegalAcceptanceStore
import com.example.data.notifications.NotificationCenter
import com.example.data.notifications.NotificationPreferences
import com.example.i18n.AppStrings
import com.example.i18n.LocalePreferences
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android permission prompt and the resulting notification delivery. */
@RunWith(AndroidJUnit4::class)
class NativeNotificationPermissionTest {
  @get:Rule val composeRule = createEmptyComposeRule()
  private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
  private val context get() = instrumentation.targetContext
  private val modelStore = ViewModelStore()
  private var scenario: ActivityScenario<MainActivity>? = null
  private var fixture: ScreenJourneyFixture? = null

  @Before fun resetDisposableInstallation() {
    context.getSharedPreferences("legal_acceptance", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("nextdns_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    runBlocking { NotificationPreferences(context).clearAccountState() }
    LocalePreferences.setSelection(context, "en")
  }

  @After fun closeFixture() {
    context.getSystemService(NotificationManager::class.java).cancelAll()
    scenario?.close()
    instrumentation.runOnMainSync { modelStore.clear() }
    fixture?.close()
    runBlocking { NotificationPreferences(context).clearAccountState() }
  }

  private fun launchSettings() {
    assertTrue(LegalAcceptanceStore(context).acceptCurrentTerms())
    scenario = ActivityScenario.launch(MainActivity::class.java)
    val backend = ScreenJourneyFixture(context).also { fixture = it }
    instrumentation.runOnMainSync { modelStore.put("notifications", backend.model) }
    scenario!!.onActivity { activity ->
      activity.setContent { AppTheme { HomeScreen(backend.model) } }
    }
    composeRule.waitUntil(20_000) {
      composeRule.onAllNodesWithTag("tab_settings").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("tab_settings").performScrollTo().performClick()
    composeRule.waitUntil(20_000) {
      composeRule.onAllNodesWithTag("settings_scroll_list").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("settings_scroll_list")
      .performScrollToNode(hasTestTag("settings_section_app_preferences"))
  }

  private fun notificationToggle(title: String) =
    composeRule.onNode(isToggleable() and hasText(title))

  private fun permissionDialogButton(resourceName: String): Boolean {
    val device = UiDevice.getInstance(instrumentation)
    val packages = listOf("com.google.android.permissioncontroller", "com.android.permissioncontroller")
    val button = packages.asSequence().mapNotNull { packageName ->
      device.wait(
        Until.findObject(By.res("$packageName:id/$resourceName")),
        2_000
      )
    }.firstOrNull()
    if (button == null) return false
    button.click()
    return true
  }

  @Test fun permissionDenialAndGrantFollowThePlatformPrompt() {
    val permission = Manifest.permission.POST_NOTIFICATIONS
    val manager = context.getSystemService(NotificationManager::class.java)
    launchSettings()
    NotificationCenter.createChannels(context)
    manager.cancelAll()

    if (Build.VERSION.SDK_INT >= 33) {
      assertEquals(
        "A new app installation must begin with notification permission denied",
        PackageManager.PERMISSION_DENIED,
        context.checkSelfPermission(permission)
      )
      val configTitle = AppStrings.get(R.string.ui_d2a366ccf5)
      notificationToggle(configTitle).performScrollTo().performClick()
      assertTrue("Android notification permission dialog did not appear", permissionDialogButton("permission_deny_button"))
      composeRule.waitUntil(5_000) {
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED
      }
      NotificationRuntimeAssertions.assertDeniedPermissionPreventsNotifications(context)

      notificationToggle(configTitle).performClick()
      assertTrue("Android notification permission dialog did not reappear", permissionDialogButton("permission_allow_button"))
      composeRule.waitUntil(10_000) {
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
      }
      assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(permission))
    }

    NotificationRuntimeAssertions.assertPrivateNotificationsHaveGenericPublicVersions(context)
  }
}
