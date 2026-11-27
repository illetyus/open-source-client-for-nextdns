package com.example.data.local

import com.example.R
import com.example.i18n.AppStrings

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.*
import com.example.data.security.AndroidKeystoreApiKeyProtector
import com.example.data.security.ApiKeyProtector
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class NextDnsPreferences(
  context: Context,
  private val apiKeyProtector: ApiKeyProtector = AndroidKeystoreApiKeyProtector()
) {
  private val prefs: SharedPreferences = context.getSharedPreferences("nextdns_secure_prefs", Context.MODE_PRIVATE)

  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  init {
    purgeLegacyDnsLogCache()
    purgeLegacyLocalAccountMetadata()
  }

  private fun purgeLegacyLocalAccountMetadata() {
    prefs.edit()
      .remove("saved_user_email")
      .remove("saved_user_name")
      .apply()
  }

  private fun purgeLegacyDnsLogCache() {
    val legacyLogKeys = prefs.all.keys.filter { it.startsWith("saved_logs_") }
    if (legacyLogKeys.isEmpty()) return

    val editor = prefs.edit()
    legacyLogKeys.forEach { editor.remove(it) }
    editor.apply()
  }

  companion object {
    private const val KEY_API_KEY = "saved_api_key"
    private const val KEY_API_KEY_ENCRYPTED = "saved_api_key_encrypted_v1"
    private const val KEY_ACTIVE_PROFILE_ID = "saved_active_profile_id"
    private const val KEY_GUEST_MODE = "saved_guest_mode"
    private const val KEY_AUTO_SYNC_INTERVAL = "saved_auto_sync_interval"
    private const val KEY_PROFILES = "saved_profiles_json"

    @Volatile
    private var INSTANCE: NextDnsPreferences? = null

    fun getInstance(context: Context): NextDnsPreferences {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: NextDnsPreferences(context.applicationContext).also { INSTANCE = it }
      }
    }
  }

  private val apiKeyLock = Any()

  var apiKey: String
    get() = synchronized(apiKeyLock) {
      val encrypted = prefs.getString(KEY_API_KEY_ENCRYPTED, null)
      if (!encrypted.isNullOrBlank()) {
        val decrypted = runCatching {
          apiKeyProtector.decrypt(encrypted)
        }.getOrNull()

        if (!decrypted.isNullOrBlank()) {
          return@synchronized decrypted
        }

        // Corrupt or non-decryptable ciphertext must never fall back to being
        // interpreted as a credential. Remove it, then try a legacy plaintext
        // value only if an upgrade was interrupted before migration completed.
        prefs.edit().remove(KEY_API_KEY_ENCRYPTED).commit()
      }

      val legacyPlaintext = prefs.getString(KEY_API_KEY, null).orEmpty()
      if (legacyPlaintext.isBlank()) return@synchronized ""

      val migrated = runCatching {
        apiKeyProtector.encrypt(legacyPlaintext)
      }.getOrNull()

      if (migrated == null) {
        prefs.edit()
          .remove(KEY_API_KEY)
          .remove(KEY_API_KEY_ENCRYPTED)
          .commit()
        return@synchronized ""
      }

      val committed = prefs.edit()
        .putString(KEY_API_KEY_ENCRYPTED, migrated)
        .remove(KEY_API_KEY)
        .commit()

      if (!committed) {
        prefs.edit()
          .remove(KEY_API_KEY)
          .remove(KEY_API_KEY_ENCRYPTED)
          .commit()
        return@synchronized ""
      }

      legacyPlaintext
    }
    set(value) = synchronized(apiKeyLock) {
      if (value.isBlank()) {
        prefs.edit()
          .remove(KEY_API_KEY_ENCRYPTED)
          .remove(KEY_API_KEY)
          .commit()
        return@synchronized
      }

      val encrypted = runCatching {
        apiKeyProtector.encrypt(value)
      }.getOrElse {
        prefs.edit()
          .remove(KEY_API_KEY_ENCRYPTED)
          .remove(KEY_API_KEY)
          .commit()
        throw IllegalStateException(AppStrings.get(R.string.ui_7bd985c876), it)
      }

      val committed = prefs.edit()
        .putString(KEY_API_KEY_ENCRYPTED, encrypted)
        .remove(KEY_API_KEY)
        .commit()

      check(committed) {
        AppStrings.get(R.string.ui_c2a8fa4d41)
      }
    }

  var activeProfileId: String
    get() = prefs.getString(KEY_ACTIVE_PROFILE_ID, "") ?: ""
    set(value) = prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, value).apply()

  var isGuestMode: Boolean
    get() = prefs.getBoolean(KEY_GUEST_MODE, false)
    set(value) = prefs.edit().putBoolean(KEY_GUEST_MODE, value).apply()

  var autoSyncIntervalSec: Int
    get() = prefs.getInt(KEY_AUTO_SYNC_INTERVAL, 15)
    set(value) = prefs.edit().putInt(KEY_AUTO_SYNC_INTERVAL, value).apply()

  fun saveProfiles(profiles: List<NextDnsProfile>) {
    try {
      val type = Types.newParameterizedType(List::class.java, NextDnsProfile::class.java)
      val adapter = moshi.adapter<List<NextDnsProfile>>(type)
      prefs.edit().putString(KEY_PROFILES, adapter.toJson(profiles)).apply()
    } catch (_: Exception) {}
  }

  fun getProfiles(): List<NextDnsProfile>? {
    val json = prefs.getString(KEY_PROFILES, null) ?: return null
    return try {
      val type = Types.newParameterizedType(List::class.java, NextDnsProfile::class.java)
      val adapter = moshi.adapter<List<NextDnsProfile>>(type)
      adapter.fromJson(json)
    } catch (_: Exception) {
      null
    }
  }

  fun saveSecuritySettings(profileId: String, settings: SecuritySettings) {
    try {
      val adapter = moshi.adapter(SecuritySettings::class.java)
      prefs.edit().putString("saved_sec_$profileId", adapter.toJson(settings)).apply()
    } catch (_: Exception) {}
  }

  fun getSecuritySettings(profileId: String): SecuritySettings? {
    val json = prefs.getString("saved_sec_$profileId", null) ?: return null
    return try {
      moshi.adapter(SecuritySettings::class.java).fromJson(json)
    } catch (_: Exception) {
      null
    }
  }

  fun savePrivacySettings(profileId: String, settings: PrivacySettings) {
    try {
      val adapter = moshi.adapter(PrivacySettings::class.java)
      prefs.edit().putString("saved_privacy_v2_$profileId", adapter.toJson(settings)).apply()
    } catch (_: Exception) {}
  }

  fun getPrivacySettings(profileId: String): PrivacySettings? {
    val json = prefs.getString("saved_privacy_v2_$profileId", null) ?: return null
    return try {
      moshi.adapter(PrivacySettings::class.java).fromJson(json)
    } catch (_: Exception) {
      null
    }
  }

  fun saveParentalControlSettings(profileId: String, settings: ParentalControlSettings) {
    try {
      val adapter = moshi.adapter(ParentalControlSettings::class.java)
      prefs.edit().putString("saved_par_$profileId", adapter.toJson(settings)).apply()
    } catch (_: Exception) {}
  }

  fun getParentalControlSettings(profileId: String): ParentalControlSettings? {
    val json = prefs.getString("saved_par_$profileId", null) ?: return null
    return try {
      moshi.adapter(ParentalControlSettings::class.java).fromJson(json)
    } catch (_: Exception) {
      null
    }
  }

  fun saveDenylist(profileId: String, items: List<AllowDenyItem>) {
    try {
      val type = Types.newParameterizedType(List::class.java, AllowDenyItem::class.java)
      val adapter = moshi.adapter<List<AllowDenyItem>>(type)
      prefs.edit().putString("saved_deny_$profileId", adapter.toJson(items)).apply()
    } catch (_: Exception) {}
  }

  fun getDenylist(profileId: String): List<AllowDenyItem>? {
    val json = prefs.getString("saved_deny_$profileId", null) ?: return null
    return try {
      val type = Types.newParameterizedType(List::class.java, AllowDenyItem::class.java)
      val adapter = moshi.adapter<List<AllowDenyItem>>(type)
      adapter.fromJson(json)
    } catch (_: Exception) {
      null
    }
  }

  fun saveAllowlist(profileId: String, items: List<AllowDenyItem>) {
    try {
      val type = Types.newParameterizedType(List::class.java, AllowDenyItem::class.java)
      val adapter = moshi.adapter<List<AllowDenyItem>>(type)
      prefs.edit().putString("saved_allow_$profileId", adapter.toJson(items)).apply()
    } catch (_: Exception) {}
  }

  fun getAllowlist(profileId: String): List<AllowDenyItem>? {
    val json = prefs.getString("saved_allow_$profileId", null) ?: return null
    return try {
      val type = Types.newParameterizedType(List::class.java, AllowDenyItem::class.java)
      val adapter = moshi.adapter<List<AllowDenyItem>>(type)
      adapter.fromJson(json)
    } catch (_: Exception) {
      null
    }
  }

  fun saveConfigSettings(profileId: String, settings: ConfigSettings) {
    try {
      val adapter = moshi.adapter(ConfigSettings::class.java)
      prefs.edit().putString("saved_cfg_$profileId", adapter.toJson(settings)).apply()
    } catch (_: Exception) {}
  }

  fun getConfigSettings(profileId: String): ConfigSettings? {
    val json = prefs.getString("saved_cfg_$profileId", null) ?: return null
    return try {
      moshi.adapter(ConfigSettings::class.java).fromJson(json)
    } catch (_: Exception) {
      null
    }
  }

  /** Synchronous deletion result is required before reporting a successful sign-out. */
  fun clear(): Boolean = synchronized(apiKeyLock) {
    prefs.edit().clear().commit()
  }
}

