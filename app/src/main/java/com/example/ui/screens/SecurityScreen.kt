package com.example.ui.screens

import com.example.R
import com.example.i18n.AppStrings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NextDnsViewModel
import com.example.data.repository.TldCatalogLoadState
import com.example.data.api.SecurityFieldDiscovery
import com.example.data.api.SecurityJsonValueType

@Composable
fun SecurityScreen(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val settings by viewModel.securitySettings.collectAsStateWithLifecycle()
  val unverifiedFields by viewModel.unverifiedSecurityFields.collectAsStateWithLifecycle()
  var showAddTldDialog by remember { mutableStateOf(false) }
  var newTldInput by remember { mutableStateOf("") }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .testTag("security_settings_list"),
    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. Tehdit İstihbaratı Beslemeleri
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_9c19e93b43),
        subtitle = AppStrings.get(R.string.ui_3e2b9e54d4),
        checked = settings.threatIntelligenceFeeds,
        onCheckedChange = { viewModel.toggleSecurityFeature("threatIntelligenceFeeds", it) }
      )
    }

    // 2. Yapay Zekâ Destekli Tehdit Algılama [BETA]
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_b8c859e123),
        subtitle = AppStrings.get(R.string.ui_be3c39ca5b),
        checked = settings.aiThreatDetection,
        onCheckedChange = { viewModel.toggleSecurityFeature("aiThreatDetection", it) },
        isBeta = true
      )
    }

    // 3. Google Güvenli Tarama
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_b6b6e376c7),
        subtitle = AppStrings.get(R.string.ui_e8957127ba),
        checked = settings.googleSafeBrowsing,
        onCheckedChange = { viewModel.toggleSecurityFeature("googleSafeBrowsing", it) }
      )
    }

    // 4. Kripto Korsanlık (Cryptojacking) Koruması
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_309e669643),
        subtitle = AppStrings.get(R.string.ui_c5806dad80),
        checked = settings.cryptojacking,
        onCheckedChange = { viewModel.toggleSecurityFeature("cryptojacking", it) }
      )
    }

    // 5. DNS Rebinding Koruması
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_7a41105eb3),
        subtitle = AppStrings.get(R.string.ui_9bf918d253),
        checked = settings.dnsRebinding,
        onCheckedChange = { viewModel.toggleSecurityFeature("dnsRebinding", it) }
      )
    }

    // 6. IDN Eşyazımı Saldırı Koruması
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_9904d77eb3),
        subtitle = AppStrings.get(R.string.idn_description),
        checked = settings.idnHomographs,
        onCheckedChange = { viewModel.toggleSecurityFeature("idnHomographs", it) }
      )
    }

    // 7. Yanlış Siteye Yönlendirme Koruması
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_6194b3ff35),
        subtitle = AppStrings.get(R.string.ui_cf6cbd0dc8),
        checked = settings.typosquatting,
        onCheckedChange = { viewModel.toggleSecurityFeature("typosquatting", it) }
      )
    }

    // 8. Alan Adı Oluşturma Algoritmaları (DGA) Koruması
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_fa2b128b73),
        subtitle = AppStrings.get(R.string.ui_31c1f6c610),
        checked = settings.dga,
        onCheckedChange = { viewModel.toggleSecurityFeature("dga", it) }
      )
    }

    // 9. Yeni Kaydedilmiş Alan Adlarını (NRD'ler) Engelle
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_c3cd82c4f0),
        subtitle = AppStrings.get(R.string.ui_1371e307ce),
        checked = settings.nrd,
        onCheckedChange = { viewModel.toggleSecurityFeature("nrd", it) }
      )
    }

    // 10. Dinamik DNS Ana Bilgisayar Adlarını Engelle [BETA]
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_3399d9a672),
        subtitle = AppStrings.get(R.string.ddns_description),
        checked = settings.ddns,
        onCheckedChange = { viewModel.toggleSecurityFeature("ddns", it) },
        isBeta = true
      )
    }

    // 11. Park Edilmiş Alan Adlarını Engelle
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_b8b2a448eb),
        subtitle = AppStrings.get(R.string.ui_b1fff1587d),
        checked = settings.parkedDomains,
        onCheckedChange = { viewModel.toggleSecurityFeature("parkedDomains", it) }
      )
    }

    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_data_drop_services_title),
        checked = settings.dataDropServices,
        feature = "dataDropServices",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_decentralized_web_gateways_title),
        checked = settings.decentralizedWebGateways,
        feature = "decentralizedWebGateways",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_dns_data_exfiltration_title),
        checked = settings.dnsDataExfiltration,
        feature = "dnsDataExfiltration",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_dns_payload_delivery_title),
        checked = settings.dnsPayloadDelivery,
        feature = "dnsPayloadDelivery",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_fast_flux_networks_title),
        checked = settings.fastFluxNetworks,
        feature = "fastFluxNetworks",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_free_hosting_domains_title),
        checked = settings.freeHostingDomains,
        feature = "freeHostingDomains",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_high_risk_tlds_title),
        checked = settings.highRiskTlds,
        feature = "highRiskTlds",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_newly_active_domains_title),
        checked = settings.newlyActiveDomains,
        feature = "newlyActiveDomains",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_residential_hosting_title),
        checked = settings.residentialHosting,
        feature = "residentialHosting",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_tunneling_endpoints_title),
        checked = settings.tunnelingEndpoints,
        feature = "tunnelingEndpoints",
        viewModel = viewModel
      )
    }
    item {
      ObservedSecurityToggle(
        title = AppStrings.get(R.string.security_untrusted_certificates_title),
        checked = settings.untrustedCertificates,
        feature = "untrustedCertificates",
        viewModel = viewModel
      )
    }

    // 12. Üst Seviye Alan Adlarını (TLD'ler) Engelle
    item {
      NextDnsCard(
        title = AppStrings.get(R.string.ui_bfa7376292),
        subtitle = AppStrings.get(R.string.ui_8a22df1169)
      ) {
        if (settings.blockedTlds.isNotEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            settings.blockedTlds.forEach { tld ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = ".$tld",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                  )
                  IconButton(
                    onClick = { viewModel.removeBlockedTld(tld) },
                    modifier = Modifier.size(48.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = AppStrings.get(R.string.ui_b88019aa28),
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }
        }

        NextDnsButton(
          text = AppStrings.get(R.string.ui_2e2a1e330e),
          onClick = { showAddTldDialog = true },
          icon = Icons.Default.Add
        )
      }
    }

    // 13. Çocukların Cinsel İstismarına İlişkin Materyalleri Engelle
    item {
      NextDnsSettingToggle(
        title = AppStrings.get(R.string.ui_122c713cab),
        subtitle = AppStrings.get(R.string.ui_41ecbffa11),
        checked = settings.csam,
        onCheckedChange = { viewModel.toggleSecurityFeature("csam", it) }
      )
    }
    if (unverifiedFields.fields.isNotEmpty() || unverifiedFields.omittedCount > 0) {
      item { UnverifiedSecurityFieldsSection(unverifiedFields) }
    }
  }

  // TLD Ekle Dialog
  if (showAddTldDialog) {
    val liveTldCatalog by viewModel.availableTldsCatalog.collectAsStateWithLifecycle()
    val tldCatalogLoadState by viewModel.tldCatalogLoadState.collectAsStateWithLifecycle()
    var tldSearchQuery by remember { mutableStateOf("") }

    val availableTlds = liveTldCatalog
      .map { it.id }
      .map { if (it.startsWith(".")) it else ".$it" }
      .filter { tld ->
        !settings.blockedTlds.any { added -> added.equals(tld.removePrefix("."), ignoreCase = true) }
      }
      .filter { tld ->
        tldSearchQuery.isBlank() || tld.contains(tldSearchQuery.trim().removePrefix("."), ignoreCase = true)
      }

    AlertDialog(
      onDismissRequest = { showAddTldDialog = false },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth().height(600.dp),
      title = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(AppStrings.get(R.string.ui_a0a234f76a), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            IconButton(onClick = { showAddTldDialog = false }, modifier = Modifier.size(48.dp)) {
              Icon(Icons.Default.Close, contentDescription = AppStrings.get(R.string.ui_7b31a9fc48), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          OutlinedTextField(
            value = tldSearchQuery,
            onValueChange = { tldSearchQuery = it },
            placeholder = { Text(AppStrings.get(R.string.ui_d812357041), fontSize = 12.sp) },
            singleLine = true,
            enabled = tldCatalogLoadState == TldCatalogLoadState.AVAILABLE,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
          )
        }
      },
      text = {
        TldCatalogContent(
          loadState = tldCatalogLoadState,
          tlds = availableTlds,
          onAddTld = { viewModel.addBlockedTld(it) }
        )
      },
      confirmButton = {},
      dismissButton = {}
    )
  }
}

@Composable
private fun ObservedSecurityToggle(
  title: String,
  checked: Boolean,
  feature: String,
  viewModel: NextDnsViewModel
) {
  NextDnsSettingToggle(
    title = title,
    subtitle = AppStrings.get(R.string.security_api_toggle_description),
    checked = checked,
    onCheckedChange = { viewModel.toggleSecurityFeature(feature, it) },
    modifier = Modifier.testTag("security_flag_$feature")
  )
}

@Composable
internal fun UnverifiedSecurityFieldsSection(discovery: SecurityFieldDiscovery) {
  if (discovery.fields.isEmpty() && discovery.omittedCount == 0) return

  NextDnsCard(
    title = AppStrings.get(R.string.security_unverified_title),
    subtitle = AppStrings.get(R.string.security_unverified_explanation)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      discovery.fields.forEach { field ->
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("security_unverified_field_${field.name}"),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Text(
            text = field.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
          )
          Text(
            text = when (field.valueType) {
              SecurityJsonValueType.BOOLEAN -> AppStrings.get(
                R.string.security_unverified_boolean,
                field.booleanValue.toString()
              )
              SecurityJsonValueType.STRING -> AppStrings.get(R.string.security_unverified_text)
              SecurityJsonValueType.NUMBER -> AppStrings.get(R.string.security_unverified_number)
              SecurityJsonValueType.OBJECT -> AppStrings.get(R.string.security_unverified_object)
              SecurityJsonValueType.ARRAY -> AppStrings.get(R.string.security_unverified_array)
              SecurityJsonValueType.NULL -> AppStrings.get(R.string.security_unverified_null)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
          )
        }
      }
      if (discovery.omittedCount > 0) {
        Text(
          text = AppStrings.get(R.string.security_unverified_omitted, discovery.omittedCount),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp
        )
      }
    }
  }
}

@Composable
internal fun TldCatalogContent(
  loadState: TldCatalogLoadState,
  tlds: List<String>,
  onAddTld: (String) -> Unit
) {
  when (loadState) {
    TldCatalogLoadState.LOADING -> CatalogMessage(
      text = AppStrings.get(R.string.security_tld_catalog_loading),
      tag = "security_tld_catalog_loading"
    )
    TldCatalogLoadState.UNAVAILABLE -> CatalogMessage(
      text = AppStrings.get(R.string.security_tld_catalog_unavailable),
      tag = "security_tld_catalog_unavailable"
    )
    TldCatalogLoadState.EMPTY -> CatalogMessage(
      text = AppStrings.get(R.string.security_tld_catalog_empty),
      tag = "security_tld_catalog_empty"
    )
    TldCatalogLoadState.AVAILABLE -> LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
      items(tlds) { tld ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(tld, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
          Button(
            onClick = { onAddTld(tld.removePrefix(".")) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(28.dp)
          ) {
            Text(AppStrings.get(R.string.add), fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
      }
    }
  }
}

@Composable
private fun CatalogMessage(text: String, tag: String) {
  Box(
    modifier = Modifier.fillMaxWidth().height(180.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag(tag)
    )
  }
}
