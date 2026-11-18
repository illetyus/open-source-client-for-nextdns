package com.example.i18n

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import com.example.NextDnsApp
import java.util.Locale
import java.text.NumberFormat

/** Resolve on each call: workers and retained ViewModels must not freeze the old locale. */
object AppStrings {
  val locale: Locale
    get() = Locale.forLanguageTag(LocalePreferences.resolvedLanguage(NextDnsApp.instance))

  fun percent(value: Number): String = NumberFormat.getPercentInstance(locale).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
  }.format(value.toDouble() / 100.0)

  fun get(@StringRes id: Int, vararg arguments: Any): String {
    val context = LocalePreferences.localizedContext(NextDnsApp.instance)
    return if (arguments.isEmpty()) context.getString(id) else context.getString(id, *arguments)
  }

  internal fun localizedResources(): Resources =
    LocalePreferences.localizedContext(NextDnsApp.instance).resources

  fun plural(@PluralsRes id: Int, quantity: Long): String =
    plural(localizedResources(), id, quantity)

  internal fun plural(resources: Resources, @PluralsRes id: Int, quantity: Long): String =
    resources.getQuantityString(id, quantity.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), quantity)
}
