package com.example.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.example.NextDnsApp
import com.example.R
import com.example.data.api.LogRetentionCodec
import com.example.data.legal.LegalAcceptanceStore
import com.example.ui.viewmodel.NavTab
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NextDnsApp::class)
class LocalePreferencesTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test fun allFiveChoicesResolveResourcesWithoutChangingApiValues() {
    val titles = listOf("Settings", "Ayarlar", "Einstellungen", "Paramètres", "Ajustes")
    LocalePreferences.supportedLanguages.forEachIndexed { i, language ->
      assertTrue(LocalePreferences.setSelection(context, language))
      assertEquals(language, LocalePreferences.resolvedLanguage(context))
      assertEquals(titles[i], NavTab.SETTINGS.title)
      assertEquals(86_400, LogRetentionCodec.toSeconds("1 gün"))
      assertEquals("1 gün", LogRetentionCodec.toLabel(86_400))
      assertFalse(UiLabels.canonical("İsviçre (CH)").isBlank())
      assertTrue(AppStrings.plural(R.plurals.minutes_ago, 2).contains("2"))
    }
  }

  @Test fun changingLanguagePreservesTermsRevision() {
    val store = LegalAcceptanceStore(context)
    assertTrue(store.acceptCurrentTerms())
    LocalePreferences.setSelection(context, "fr")
    assertTrue(store.isAccepted())
    assertEquals("fr", LocalePreferences.selection(context))
    LocalePreferences.setSelection(context, LocalePreferences.SYSTEM)
    assertTrue(store.isAccepted())
  }

  @Test fun unchangedApplicationConfigurationReusesLocalizedResources() {
    LocalePreferences.setSelection(context, "tr")
    val first = LocalePreferences.localizedContext(context)
    assertSame(first, LocalePreferences.localizedContext(context))
    LocalePreferences.setSelection(context, "fr")
    val second = LocalePreferences.localizedContext(context)
    assertNotSame(first, second)
    assertEquals("fr", second.resources.configuration.locales[0].language)
  }

  @Test fun overriddenFontScaleIsNotLostOrReusedFromApplication() {
    LocalePreferences.setSelection(context, "de")
    val configuration = android.content.res.Configuration(context.resources.configuration)
    configuration.fontScale = 1.7f
    val windowContext = context.createConfigurationContext(configuration)
    assertEquals(1.7f, LocalePreferences.localizedContext(windowContext).resources.configuration.fontScale)
    assertEquals("de", LocalePreferences.localizedContext(windowContext).resources.configuration.locales[0].language)
  }

  @Test fun invalidSavedChoiceFallsBackToSystem() {
    context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit()
      .putString("language", "invalid").commit()
    assertEquals(LocalePreferences.SYSTEM, LocalePreferences.selection(context))
    assertTrue(LocalePreferences.resolvedLanguage(context) in LocalePreferences.supportedLanguages)
  }

  @Test @Config(sdk = [34]) fun platformAndInAppChoicesStaySynchronized() {
    val manager = context.getSystemService(LocaleManager::class.java)
    LocalePreferences.setSelection(context, "de")
    assertEquals("de", manager.applicationLocales[0].language)
    manager.applicationLocales = LocaleList.forLanguageTags("es")
    assertEquals("es", LocalePreferences.selection(context))
    assertEquals("es", LocalePreferences.resolvedLanguage(context))
    manager.applicationLocales = LocaleList.forLanguageTags("ja")
    assertEquals("en", LocalePreferences.resolvedLanguage(context))
    LocalePreferences.setSelection(context, LocalePreferences.SYSTEM)
    assertTrue(manager.applicationLocales.isEmpty)
  }
}
