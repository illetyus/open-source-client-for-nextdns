package com.example.data.diagnostics

import android.content.Context

internal data class SavedSpeedTestServer(
  val baseUrl: String,
  val privacyPolicyUrl: String,
  val backendPath: String
)

/** Stores only the user-entered server location and its policy link on this device. */
internal class SpeedTestSettingsStore(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFERENCES_NAME,
    Context.MODE_PRIVATE
  )

  fun read(): SavedSpeedTestServer? {
    val baseUrl = preferences.getString(KEY_BASE_URL, null)?.takeIf(String::isNotBlank) ?: return null
    val privacyPolicyUrl = preferences.getString(KEY_PRIVACY_POLICY_URL, null)?.takeIf(String::isNotBlank)
      ?: return null
    val backendPath = preferences.getString(KEY_BACKEND_PATH, DEFAULT_BACKEND_PATH) ?: DEFAULT_BACKEND_PATH
    return SavedSpeedTestServer(baseUrl, privacyPolicyUrl, backendPath)
  }

  fun save(server: SavedSpeedTestServer) {
    preferences.edit()
      .putString(KEY_BASE_URL, server.baseUrl.trim())
      .putString(KEY_PRIVACY_POLICY_URL, server.privacyPolicyUrl.trim())
      .putString(KEY_BACKEND_PATH, server.backendPath.trim().trim('/'))
      .apply()
  }

  fun clear() {
    preferences.edit()
      .remove(KEY_BASE_URL)
      .remove(KEY_PRIVACY_POLICY_URL)
      .remove(KEY_BACKEND_PATH)
      .apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "speed_test_local_settings"
    const val KEY_BASE_URL = "server_base_url"
    const val KEY_PRIVACY_POLICY_URL = "server_privacy_policy_url"
    const val KEY_BACKEND_PATH = "server_backend_path"
    const val DEFAULT_BACKEND_PATH = "backend"
  }
}
