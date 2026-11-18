package com.example.performance

import java.util.Locale

/** Fixture URL policy only; the production hostname validator is unchanged. */
internal enum class BenchmarkIconMode(val argument: String, val preload: Boolean) {
  UNIQUE("unique", false),
  REPEATED("repeated", false),
  WARM_REPEATED("warm-repeated", true);

  fun pathsFor(domain: String): List<String> {
    // UNIQUE preserves the original fixture's raw UTF-8 hex/scale behavior.
    val value = if (this == UNIQUE) domain else "benchmark-repeat.invalid"
    val key = value.toByteArray(Charsets.UTF_8).joinToString("") {
      "%02x".format(Locale.ROOT, it.toInt() and 0xff)
    }
    return listOf("3x", "2x", "1x").map { "favicons/$key@$it.png" }
  }

  companion object {
    fun fromArgument(value: String?): BenchmarkIconMode =
      if (value == null) UNIQUE else entries.firstOrNull { it.argument == value }
        ?: throw IllegalArgumentException("Unknown benchmark icon workload")
  }
}
