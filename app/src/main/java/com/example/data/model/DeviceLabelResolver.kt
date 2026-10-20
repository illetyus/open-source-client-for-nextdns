package com.example.data.model

import java.util.Locale

/** Keeps API device IDs available for filtering while preventing IDs and placeholders from becoming labels. */
object DeviceLabelResolver {
  private val placeholders = setOf(
    "undefined",
    "null",
    "unknown",
    "unknown device",
    "unidentified",
    "unidentified device",
    "bilinmeyen",
    "bilinmeyen cihaz",
    "tanımlanamayan cihaz",
    NEXTDNS_UNIDENTIFIED_DEVICE_ID.lowercase(Locale.ROOT)
  )

  fun resolve(name: String?, model: String?, id: String?, unidentifiedLabel: String): String {
    val normalizedId = id?.trim()
    return sequenceOf(name, model)
      .mapNotNull { candidate -> candidate?.trim()?.takeIf { value ->
        value.isNotEmpty() &&
          !value.equals(unidentifiedLabel.trim(), ignoreCase = true) &&
          !value.equals(normalizedId, ignoreCase = true) &&
          value.lowercase(Locale.ROOT) !in placeholders
      } }
      .firstOrNull()
      ?: unidentifiedLabel
  }
}
