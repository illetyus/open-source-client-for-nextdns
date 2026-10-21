package com.example.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Removes local settings belonging to a feature that is no longer shipped. */
suspend fun clearRetiredSpeedTestSettings(context: Context): Boolean = withContext(Dispatchers.IO) {
  try {
    val preferences = context.applicationContext
      .getSharedPreferences("speed_test_local_settings", Context.MODE_PRIVATE)
    preferences.all.isEmpty() || preferences.edit().clear().commit()
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (error: Exception) {
    Log.w(TAG, "Could not clear retired speed-test settings", error)
    false
  }
}

private const val TAG = "RetiredFeaturePrefs"
