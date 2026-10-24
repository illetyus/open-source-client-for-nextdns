package com.example.data.notifications

import com.example.data.api.AllowDenyRuleDto
import com.example.data.api.BlocklistDto
import com.example.data.api.NativeTrackingDto
import com.example.data.api.ParentalCategoryDto
import com.example.data.api.ParentalControlDto
import com.example.data.api.ParentalServiceDto
import com.example.data.api.PrivacyDto
import com.example.data.api.SecurityDto
import com.example.data.api.SettingsBlockPageDto
import com.example.data.api.SettingsDto
import com.example.data.api.SettingsLogsDto
import com.example.data.api.SettingsLogsDropDto
import com.example.data.api.SettingsPerformanceDto
import com.example.data.api.TldDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConfigSnapshotHasherTest {

  @Test
  fun digestIsOrderIndependentForRuleListsAndCatalogSelections() {
    val first = sampleSnapshot(
      denylist = listOf(
        AllowDenyRuleDto("b.example", true),
        AllowDenyRuleDto("a.example", false)
      ),
      allowlist = listOf(
        AllowDenyRuleDto("safe-b.example", true),
        AllowDenyRuleDto("safe-a.example", true)
      )
    )

    val reordered = first.copy(
      security = first.security.copy(
        tlds = first.security.tlds.orEmpty().reversed()
      ),
      privacy = first.privacy.copy(
        blocklists = first.privacy.blocklists.orEmpty().reversed(),
        natives = first.privacy.natives.orEmpty().reversed()
      ),
      parental = first.parental.copy(
        services = first.parental.services.orEmpty().reversed(),
        categories = first.parental.categories.orEmpty().reversed()
      ),
      denylist = first.denylist.reversed(),
      allowlist = first.allowlist.reversed()
    )

    assertEquals(
      ConfigSnapshotHasher.digest(first),
      ConfigSnapshotHasher.digest(reordered)
    )
  }

  @Test
  fun digestChangesWhenUserConfigurationChanges() {
    val first = sampleSnapshot()
    val changed = first.copy(
      security = first.security.copy(
        googleSafeBrowsing = false
      )
    )

    assertNotEquals(
      ConfigSnapshotHasher.digest(first),
      ConfigSnapshotHasher.digest(changed)
    )
  }

  @Test
  fun digestChangesWhenBypassAgeVerificationChanges() {
    val first = sampleSnapshot()
    val changed = first.copy(
      settings = first.settings.copy(bav = true)
    )

    assertNotEquals(
      ConfigSnapshotHasher.digest(first),
      ConfigSnapshotHasher.digest(changed)
    )
  }

  @Test
  fun digestChangesWhenAnUnverifiedServerSecurityFieldChanges() {
    val originalSecurity = sampleSnapshot().security
    val first = sampleSnapshot().copy(
      security = originalSecurity.copy(
        unknownFields = mapOf("previewFlag" to true, "nested" to mapOf("b" to 2.0, "a" to 1.0))
      )
    )
    val changed = first.copy(
      security = first.security.copy(
        unknownFields = mapOf("previewFlag" to false, "nested" to mapOf("b" to 2.0, "a" to 1.0))
      )
    )

    assertNotEquals(
      ConfigSnapshotHasher.digest(first),
      ConfigSnapshotHasher.digest(changed)
    )
  }

  @Test
  fun digestRemainsOrderIndependentForNestedUnknownServerFields() {
    val originalSecurity = sampleSnapshot().security
    val first = sampleSnapshot().copy(
      security = originalSecurity.copy(
        unknownFields = mapOf("nested" to linkedMapOf("b" to 2.0, "a" to 1.0))
      )
    )
    val reordered = first.copy(
      security = first.security.copy(
        unknownFields = mapOf("nested" to linkedMapOf("a" to 1.0, "b" to 2.0))
      )
    )

    assertEquals(
      ConfigSnapshotHasher.digest(first),
      ConfigSnapshotHasher.digest(reordered)
    )
  }

  @Test
  fun blocklistMetadataChangesDoNotCreateFalseConfigChange() {
    val first = sampleSnapshot()
    val metadataOnly = first.copy(
      privacy = first.privacy.copy(
        blocklists = listOf(
          BlocklistDto(
            id = "oisd",
            name = "Renamed upstream title",
            entries = 999_999L,
            updatedOn = "tomorrow"
          )
        )
      )
    )

    assertEquals(
      ConfigSnapshotHasher.digest(first),
      ConfigSnapshotHasher.digest(metadataOnly)
    )
  }

  private fun sampleSnapshot(
    denylist: List<AllowDenyRuleDto> = listOf(
      AllowDenyRuleDto("deny.example", true)
    ),
    allowlist: List<AllowDenyRuleDto> = listOf(
      AllowDenyRuleDto("allow.example", true)
    )
  ): NotificationConfigSnapshot =
    NotificationConfigSnapshot(
      security = SecurityDto(
        threatIntelligenceFeeds = true,
        aiThreatDetection = true,
        googleSafeBrowsing = true,
        cryptojacking = true,
        dnsRebinding = true,
        idnHomographs = true,
        typosquatting = true,
        dga = true,
        nrd = true,
        ddns = false,
        parking = true,
        csam = true,
        tlds = listOf(TldDto("zip"), TldDto("mov"))
      ),
      privacy = PrivacyDto(
        blocklists = listOf(BlocklistDto(id = "oisd")),
        natives = listOf(NativeTrackingDto("apple")),
        disguisedTrackers = true,
        allowAffiliateLinks = false
      ),
      parental = ParentalControlDto(
        services = listOf(
          ParentalServiceDto("tiktok", true),
          ParentalServiceDto("youtube", false)
        ),
        categories = listOf(
          ParentalCategoryDto("porn", true),
          ParentalCategoryDto("gambling", false)
        ),
        safeSearch = true,
        youtubeRestrictedMode = false,
        blockBypass = true
      ),
      settings = SettingsDto(
        logs = SettingsLogsDto(
          enabled = true,
          retention = 604_800,
          location = "eu",
          drop = SettingsLogsDropDto(ip = true, domain = false)
        ),
        blockPage = SettingsBlockPageDto(enabled = true),
        performance = SettingsPerformanceDto(
          ecs = false,
          cacheBoost = true,
          cnameFlattening = true
        ),
        web3 = false
      ),
      denylist = denylist,
      allowlist = allowlist
    )
}
