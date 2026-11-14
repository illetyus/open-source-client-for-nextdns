package com.example.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** UI preferences are separate from account data and survive sign-out. */
object LocalePreferences {
  val supportedLanguages = listOf("en", "tr", "de", "fr", "es")
  const val SYSTEM = "system"
  private const val STORE = "app_language"
  private const val LANGUAGE = "language"
  private data class ResourceSnapshot(val base: Context, val configuration: Configuration,
    val language: String, val localized: Context)
  private var resourceSnapshot: ResourceSnapshot? = null
  private val resourceLock = Any()

  fun selection(context: Context): String {
    if (Build.VERSION.SDK_INT >= 33) {
      val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
      return if (locales.isEmpty) SYSTEM else locales[0].language
    }
    return context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
      .getString(LANGUAGE, SYSTEM)?.takeIf { it in supportedLanguages } ?: SYSTEM
  }

  fun resolvedLanguage(context: Context): String {
    val selected = selection(context)
    if (selected != SYSTEM) return selected.takeIf { it in supportedLanguages } ?: "en"
    val systemLocales = if (Build.VERSION.SDK_INT >= 33) {
      context.getSystemService(LocaleManager::class.java).systemLocales
    } else Resources.getSystem().configuration.locales
    return (0 until systemLocales.size()).asSequence().map { systemLocales[it].language }
      .firstOrNull { it in supportedLanguages } ?: "en"
  }

  /** Only migrate a pre-Android-13 choice once; never overwrite a later system choice. */
  fun migrateToPlatform(context: Context) {
    if (Build.VERSION.SDK_INT < 33) return
    val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    if (prefs.getBoolean("platform_migrated", false)) return
    val manager = context.getSystemService(LocaleManager::class.java)
    val saved = prefs.getString(LANGUAGE, SYSTEM)
    if (manager.applicationLocales.isEmpty && saved in supportedLanguages) {
      manager.applicationLocales = LocaleList.forLanguageTags(saved)
    }
    prefs.edit().putBoolean("platform_migrated", true).apply()
  }

  fun setSelection(context: Context, language: String): Boolean {
    require(language == SYSTEM || language in supportedLanguages)
    if (Build.VERSION.SDK_INT >= 33) {
      context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (language == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
      return true
    }
    return context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
      .putString(LANGUAGE, language).commit()
  }

  fun localizedContext(context: Context): Context {
    // Cache only the application, never an Activity/window with its own override.
    // Read the current platform language and full configuration before every hit:
    // external language changes and retained ViewModels/workers stay current.
    val language = resolvedLanguage(context)
    if (context === context.applicationContext) synchronized(resourceLock) {
      val configuration = context.resources.configuration
      resourceSnapshot?.takeIf { it.base === context && it.language == language &&
        it.configuration == configuration }?.let { return it.localized }
      return createLocalizedContext(context, language).also {
        resourceSnapshot = ResourceSnapshot(context, Configuration(configuration), language, it)
      }
    }
    return createLocalizedContext(context, language)
  }

  private fun createLocalizedContext(context: Context, language: String): Context {
    val configuration = Configuration(context.resources.configuration)
    val locale = Locale.forLanguageTag(language)
    configuration.setLocales(LocaleList(locale))
    configuration.setLayoutDirection(locale)
    return context.createConfigurationContext(configuration)
  }
}
