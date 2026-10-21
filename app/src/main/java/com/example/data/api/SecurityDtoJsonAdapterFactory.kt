package com.example.data.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type
import java.security.MessageDigest

/** Retains a bounded set of unrecognized security keys without making them writable. */
internal class SecurityDtoJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(
    type: Type,
    annotations: Set<Annotation>,
    moshi: Moshi
  ): JsonAdapter<*>? {
    if (Types.getRawType(type) != SecurityDto::class.java || annotations.isNotEmpty()) return null

    val delegate = moshi.nextAdapter<SecurityDto>(this, type, annotations)
    return object : JsonAdapter<SecurityDto>() {
      override fun fromJson(reader: JsonReader): SecurityDto? {
        val json = reader.readJsonValue()
        val objectValue = json as? Map<*, *> ?: return delegate.fromJsonValue(json)
        val knownFields = LinkedHashMap<String, Any?>()
        val unknownFields = LinkedHashMap<String, Any?>()
        val overflowFields = LinkedHashMap<String, Any?>()
        var overflowCount = 0

        objectValue.forEach { (key, value) ->
          if (key !is String) return@forEach
          if (key in DOCUMENTED_SECURITY_FIELDS) {
            knownFields[key] = value
          } else if (unknownFields.size < MAX_RETAINED_UNKNOWN_SECURITY_FIELDS) {
            unknownFields[key] = value
          } else {
            overflowCount++
            overflowFields[key] = value
          }
        }

        return delegate.fromJsonValue(knownFields)?.copy(
          unknownFields = unknownFields,
          unknownFieldOverflowCount = overflowCount,
          unknownFieldOverflowDigest = SecurityUnknownFieldDigest.digest(overflowFields)
        )
      }

      override fun toJson(writer: JsonWriter, value: SecurityDto?) {
        delegate.toJson(writer, value)
      }
    }.nullSafe()
  }

  private companion object {
    const val MAX_RETAINED_UNKNOWN_SECURITY_FIELDS = 32
    val DOCUMENTED_SECURITY_FIELDS = setOf(
      "threatIntelligenceFeeds",
      "aiThreatDetection",
      "googleSafeBrowsing",
      "cryptojacking",
      "dnsRebinding",
      "idnHomographs",
      "typosquatting",
      "dga",
      "nrd",
      "ddns",
      "parking",
      "csam",
      "tlds"
    )
  }
}

enum class SecurityJsonValueType {
  BOOLEAN,
  STRING,
  NUMBER,
  OBJECT,
  ARRAY,
  NULL
}

data class UnverifiedSecurityField(
  val name: String,
  val valueType: SecurityJsonValueType,
  val booleanValue: Boolean? = null
)

data class SecurityFieldDiscovery(
  val fields: List<UnverifiedSecurityField> = emptyList(),
  val omittedCount: Int = 0
)

fun SecurityDto.discoverUnverifiedFields(maxDisplayedFields: Int = 24): SecurityFieldDiscovery {
  require(maxDisplayedFields >= 0)

  var invalidNameCount = 0
  val visibleCandidates = unknownFields.mapNotNull { (name, value) ->
    if (!name.matches(SECURITY_FIELD_NAME_PATTERN)) {
      invalidNameCount++
      return@mapNotNull null
    }
    val valueType = when (value) {
      is Boolean -> SecurityJsonValueType.BOOLEAN
      is String -> SecurityJsonValueType.STRING
      is Number -> SecurityJsonValueType.NUMBER
      is Map<*, *> -> SecurityJsonValueType.OBJECT
      is List<*> -> SecurityJsonValueType.ARRAY
      null -> SecurityJsonValueType.NULL
      else -> return@mapNotNull null
    }
    UnverifiedSecurityField(
      name = name,
      valueType = valueType,
      booleanValue = value as? Boolean
    )
  }.sortedBy { it.name }

  return SecurityFieldDiscovery(
    fields = visibleCandidates.take(maxDisplayedFields),
    omittedCount = unknownFieldOverflowCount + invalidNameCount +
      (visibleCandidates.size - maxDisplayedFields).coerceAtLeast(0)
  )
}

private val SECURITY_FIELD_NAME_PATTERN = Regex("[A-Za-z0-9_.-]{1,64}")

private object SecurityUnknownFieldDigest {
  fun digest(fields: Map<String, Any?>): String {
    if (fields.isEmpty()) return ""
    val canonical = buildString { appendCanonicalValue(fields) }
    return MessageDigest.getInstance("SHA-256")
      .digest(canonical.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
  }

  private fun StringBuilder.appendCanonicalValue(value: Any?) {
    when (value) {
      null -> append('n')
      is Boolean -> append(if (value) "b1" else "b0")
      is String -> appendSized('s', value)
      is Number -> appendSized('d', value.toString())
      is Map<*, *> -> {
        append('{')
        value.entries
          .filter { it.key is String }
          .sortedBy { it.key as String }
          .forEach { (key, nestedValue) ->
            appendSized('k', key as String)
            appendCanonicalValue(nestedValue)
          }
        append('}')
      }
      is Iterable<*> -> {
        append('[')
        value.forEach { appendCanonicalValue(it) }
        append(']')
      }
      else -> appendSized('x', value.javaClass.name)
    }
  }

  private fun StringBuilder.appendSized(type: Char, value: String) {
    append(type).append(value.length).append(':').append(value)
  }
}
