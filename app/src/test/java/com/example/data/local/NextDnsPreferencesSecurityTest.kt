package com.example.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.ApiKeyProtector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NextDnsPreferencesSecurityTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    rawPrefs().edit().clear().commit()
  }

  @After
  fun tearDown() {
    rawPrefs().edit().clear().commit()
  }

  @Test
  fun legacyPlaintextApiKey_isMigratedAndRemovedOnRead() {
    rawPrefs().edit()
      .putString(LEGACY_KEY, "legacy-secret")
      .commit()

    val preferences = NextDnsPreferences(
      context = context,
      apiKeyProtector = FakeProtector()
    )

    assertEquals("legacy-secret", preferences.apiKey)
    assertNull(rawPrefs().getString(LEGACY_KEY, null))

    val encrypted = rawPrefs().getString(ENCRYPTED_KEY, null)
    assertEquals("enc:terces-ycagel", encrypted)
    assertFalse(encrypted.orEmpty().contains("legacy-secret"))
  }

  @Test
  fun settingApiKey_neverStoresPlaintextCredential() {
    val preferences = NextDnsPreferences(
      context = context,
      apiKeyProtector = FakeProtector()
    )

    preferences.apiKey = "new-secret"

    assertNull(rawPrefs().getString(LEGACY_KEY, null))
    assertEquals("enc:terces-wen", rawPrefs().getString(ENCRYPTED_KEY, null))
    assertEquals("new-secret", preferences.apiKey)
  }

  @Test
  fun migrationEncryptionFailure_removesLegacyPlaintext() {
    rawPrefs().edit()
      .putString(LEGACY_KEY, "legacy-secret")
      .commit()

    val preferences = NextDnsPreferences(
      context = context,
      apiKeyProtector = FailingProtector()
    )

    assertTrue(preferences.apiKey.isEmpty())
    assertFalse(rawPrefs().contains(LEGACY_KEY))
    assertFalse(rawPrefs().contains(ENCRYPTED_KEY))
  }

  @Test
  fun legacySensitiveCaches_arePurgedOnInitialization() {
    rawPrefs().edit()
      .putString("saved_logs_profileA", "sensitive-query-history")
      .putString("saved_user_email", "person@example.com")
      .putString("saved_user_name", "Person")
      .putString("saved_sec_profileA", "non-log-cache")
      .commit()

    NextDnsPreferences(
      context = context,
      apiKeyProtector = FakeProtector()
    )

    assertFalse(rawPrefs().contains("saved_logs_profileA"))
    assertFalse(rawPrefs().contains("saved_user_email"))
    assertFalse(rawPrefs().contains("saved_user_name"))
    assertTrue(rawPrefs().contains("saved_sec_profileA"))
  }

  @Test
  fun clearingApiKey_removesLegacyAndEncryptedValues() {
    rawPrefs().edit()
      .putString(LEGACY_KEY, "legacy-secret")
      .putString(ENCRYPTED_KEY, "enc:terces-wen")
      .commit()

    val preferences = NextDnsPreferences(
      context = context,
      apiKeyProtector = FakeProtector()
    )

    preferences.apiKey = ""

    assertFalse(rawPrefs().contains(LEGACY_KEY))
    assertFalse(rawPrefs().contains(ENCRYPTED_KEY))
    assertTrue(preferences.apiKey.isEmpty())
  }

  @Test
  fun concurrentApiKeyReadsAndWritesRemainConsistent() {
    val preferences = NextDnsPreferences(
      context = context,
      apiKeyProtector = FakeProtector()
    )
    preferences.apiKey = "secret-initial"
    val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
    val threads = (0 until 6).map { worker ->
      Thread {
        try {
          repeat(25) { i ->
            when ((worker + i) % 3) {
              0 -> preferences.apiKey = "secret-$worker-$i"
              1 -> {
                val read = preferences.apiKey
                assertTrue(read.isEmpty() || read.startsWith("secret-"))
              }
              else -> preferences.apiKey = ""
            }
          }
        } catch (t: Throwable) {
          errors += t
        }
      }.apply { start() }
    }
    threads.forEach { it.join() }
    assertTrue(errors.isEmpty())
  }

  private fun rawPrefs() =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private class FailingProtector : ApiKeyProtector {
    override fun encrypt(plainText: String): String {
      error("simulated encryption failure")
    }

    override fun decrypt(envelope: String): String {
      error("simulated decryption failure")
    }
  }

  private class FakeProtector : ApiKeyProtector {
    override fun encrypt(plainText: String): String = "enc:${plainText.reversed()}"

    override fun decrypt(envelope: String): String {
      require(envelope.startsWith("enc:"))
      return envelope.removePrefix("enc:").reversed()
    }
  }

  companion object {
    private const val PREFS_NAME = "nextdns_secure_prefs"
    private const val LEGACY_KEY = "saved_api_key"
    private const val ENCRYPTED_KEY = "saved_api_key_encrypted_v1"
  }
}
