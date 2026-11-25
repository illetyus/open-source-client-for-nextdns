package com.example

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.legal.LegalAcceptanceStore
import com.example.data.preferences.ThemePreferences
import com.example.i18n.LocalePreferences
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Invoked in separate processes before and after installing over the published beta. */
@RunWith(AndroidJUnit4::class)
class NativeBetaUpgradeTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val seededAt = 1_790_000_000_000L
  private val betaTermsRevision = "terms-2026-10-DRAFT-2"

  @Suppress("DEPRECATION")
  private fun installedVersionCode(): Int =
    context.packageManager.getPackageInfo(context.packageName, 0).versionCode

  @Test fun seedSettingsInPublishedBeta() = runBlocking {
    assertEquals(1, installedVersionCode())
    assertFalse(LegalAcceptanceStore(context).isAccepted())
    assertTrue(LegalAcceptanceStore(context) { seededAt }.acceptCurrentTerms())
    assertTrue(LocalePreferences.setSelection(context, "tr"))
    ThemePreferences(context).setThemeMode(ThemeMode.DARK)
    NextDnsApp.preferences.isGuestMode = true
    // The production setter uses apply(). Flush pending writes before the runner exits.
    assertTrue(
      context.getSharedPreferences("nextdns_secure_prefs", Context.MODE_PRIVATE).edit().commit()
    )
  }

  @Test fun publishedBetaReadsBackSeededSettings() = runBlocking {
    assertEquals(1, installedVersionCode())
    assertStoredSettings()
  }

  @Test fun installedUpgradePreservesBetaSettings() = runBlocking {
    assertTrue(installedVersionCode() > 1)
    assertStoredSettings()
  }

  private suspend fun assertStoredSettings() {
    assertEquals("tr", LocalePreferences.selection(context))
    assertEquals(ThemeMode.DARK, ThemePreferences(context).themeMode.first())
    assertTrue("Stored guest preference should be enabled", NextDnsApp.preferences.isGuestMode)
    assertTrue(NextDnsApp.preferences.apiKey.isEmpty())

    val storedAcceptance = context.getSharedPreferences("legal_acceptance", Context.MODE_PRIVATE)
    assertEquals(betaTermsRevision, storedAcceptance.getString("accepted_terms_revision", null))
    assertEquals(seededAt, storedAcceptance.getLong("accepted_at_epoch_millis", 0L))
    val acceptance = LegalAcceptanceStore(context)
    if (LegalAcceptanceStore.CURRENT_TERMS_REVISION == betaTermsRevision) {
      assertEquals(seededAt, acceptance.acceptedAtEpochMillis())
    } else {
      assertFalse("A material revision must ask for acceptance again", acceptance.isAccepted())
    }
  }
}
