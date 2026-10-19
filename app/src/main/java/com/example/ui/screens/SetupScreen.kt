package com.example.ui.screens

import com.example.R
import com.example.i18n.AppStrings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.example.data.model.DiagnosticConnectionState
import com.example.data.model.diagnosticConnectionState
import com.example.data.model.setupIpv6Addresses
import com.example.data.model.DiagnosticTestResult
import com.example.data.model.NextDnsProfile
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NextDnsViewModel

@Composable
fun SetupScreen(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
  val testResult by viewModel.testResult.collectAsStateWithLifecycle()
  val isDiagnosticRunning by viewModel.isDiagnosticRunning.collectAsStateWithLifecycle()

  val profileSetup by viewModel.profileSetup.collectAsStateWithLifecycle()

  var selectedPlatform by remember { mutableStateOf("Android") }
  var showAdvancedIpSettings by remember { mutableStateOf(false) }

  val platforms = listOf("Android", "iOS", "Windows", "macOS", "Linux", "ChromeOS", AppStrings.get(R.string.ui_4b4de7fb15), AppStrings.get(R.string.ui_7b5d49c4a6))
  val profile = activeProfile ?: NextDnsProfile("", AppStrings.get(R.string.ui_e267d34382))
  val profileId = profile.id
  val setup = profileSetup

  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner, activeProfile?.id) {
    lifecycleOwner.lifecycle.pollDiagnostics {
      viewModel.refreshDiagnostic()
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      ConnectionStatusBanner(
        activeProfile = activeProfile,
        testResult = testResult,
        isDiagnosticRunning = isDiagnosticRunning,
        onRefreshDiagnostic = { viewModel.runDiagnostic(showToast = true) },
        onOpenDnsSettings = {
          try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
          } catch (_: Exception) {
            try {
              val intent = Intent("android.settings.NETWORK_PROVIDER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              context.startActivity(intent)
            } catch (_: Exception) {
              context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              })
            }
          }
        }
      )
    }

    item {
      EndpointsSection(
        profileId = profileId,
        context = context,
        setup = setup
      )
    }

    item {
      LinkedIpSection(
        profileId = profileId,
        setup = setup,
        testResult = testResult,
        showAdvancedIpSettings = showAdvancedIpSettings,
        onToggleAdvanced = { showAdvancedIpSettings = !showAdvancedIpSettings },
        onLinkIp = { viewModel.linkIpAddress(profileId) },
        context = context
      )
    }

    item {
      SetupGuideSection(
        profileId = profileId,
        setup = setup,
        platforms = platforms,
        selectedPlatform = selectedPlatform,
        onPlatformSelected = { selectedPlatform = it },
        context = context
      )
    }
  }
}

// =========================================================================
// Modular Sub-Composables
// =========================================================================

@Composable
internal fun ConnectionStatusBanner(
  activeProfile: NextDnsProfile?,
  testResult: DiagnosticTestResult,
  isDiagnosticRunning: Boolean,
  onRefreshDiagnostic: () -> Unit,
  onOpenDnsSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val state = diagnosticConnectionState(activeProfile, testResult, isDiagnosticRunning)
  val isTesting = state == DiagnosticConnectionState.TESTING
  val isUsingNextDns = state == DiagnosticConnectionState.MATCHED_PROFILE
  val isOffline = state == DiagnosticConnectionState.ERROR
  val unverifiedProfile = state == DiagnosticConnectionState.UNVERIFIED_PROFILE
  val noSelectedProfile = state == DiagnosticConnectionState.NO_SELECTED_PROFILE

  val borderColor = when {
    isTesting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    isUsingNextDns -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
    isOffline -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
  }

  val beaconColor = when {
    isTesting -> MaterialTheme.colorScheme.primary
    isUsingNextDns -> MaterialTheme.colorScheme.tertiary
    isOffline -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.error
  }

  val title = when {
    isTesting -> AppStrings.get(R.string.ui_5ed3c5cf66)
    isUsingNextDns -> AppStrings.get(R.string.ui_934b24bdc9)
    unverifiedProfile -> AppStrings.get(R.string.diagnostic_unverified_title)
    noSelectedProfile -> AppStrings.get(R.string.diagnostic_detected_title)
    isOffline -> AppStrings.get(R.string.ui_97c1211397)
    else -> AppStrings.get(R.string.ui_48016c5791)
  }

  val subtitle = when {
    isTesting -> AppStrings.get(R.string.ui_40f866a700)
    isUsingNextDns -> AppStrings.get(R.string.ui_ebbbefad7b)
    unverifiedProfile -> AppStrings.get(R.string.diagnostic_unverified_subtitle)
    noSelectedProfile -> AppStrings.get(R.string.diagnostic_unselected_subtitle)
    isOffline -> AppStrings.get(R.string.ui_19b9ff3444)
    !testResult.resolver.isNullOrBlank() -> AppStrings.get(R.string.current_resolver, testResult.resolver ?: AppStrings.get(R.string.unavailable))
    else -> AppStrings.get(R.string.ui_38cde25084)
  }

  val badgeText = when {
    isTesting -> AppStrings.get(R.string.ui_1e2d82ccaf)
    isUsingNextDns -> {
      val lat = if (testResult.latencyMs > 0) "${testResult.latencyMs} ms • " else ""
      val proto = testResult.protocol.takeIf { it.isNotBlank() }
      when {
        proto != null -> "$lat$proto"
        lat.isNotBlank() -> lat.removeSuffix(" • ")
        else -> AppStrings.get(R.string.ui_fb8d508c13)
      }
    }
    isOffline -> AppStrings.get(R.string.ui_47108d84ec)
    else -> AppStrings.get(R.string.ui_1866bc156e)
  }

  val badgeBg = when {
    isTesting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    isUsingNextDns -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
    isOffline -> MaterialTheme.colorScheme.surfaceVariant
    else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
  }

  val badgeTextColor = when {
    isTesting -> MaterialTheme.colorScheme.primary
    isUsingNextDns -> MaterialTheme.colorScheme.tertiary
    isOffline -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.error
  }

  val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotate")
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "rotation"
  )

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    shadowElevation = 2.dp
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        StatusBeacon(color = beaconColor, size = 10.dp, isPulsing = isUsingNextDns || isTesting)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineBreak = LineBreak.Heading),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = subtitle,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp, lineBreak = LineBreak.Paragraph),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (isUsingNextDns) {
          Surface(color = badgeBg, shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
            Text(badgeText, color = badgeTextColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
          }
        } else {
          Spacer(Modifier.weight(1f))
        }
        Surface(color = badgeBg, shape = RoundedCornerShape(12.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
          ) {
            Box(Modifier.size(6.dp).background(beaconColor, CircleShape))
            Text(AppStrings.get(R.string.ui_447d7bdd67), fontSize = 9.sp,
              fontWeight = FontWeight.Bold, color = badgeTextColor)
          }
        }
        IconButton(onClick = onRefreshDiagnostic, enabled = !isTesting, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.Refresh, contentDescription = AppStrings.get(R.string.ui_3bb3f8b024),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).then(if (isTesting) Modifier.graphicsLayer { rotationZ = rotation } else Modifier))
        }
      }

      if (!isUsingNextDns) {
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = borderColor.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .heightIn(min = 48.dp)
            .bounceClick(scaleDown = 0.98f, onClick = onOpenDnsSettings)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = AppStrings.get(R.string.ui_57efebc018),
              color = MaterialTheme.colorScheme.primary,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
          Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
internal fun EndpointsSection(
  profileId: String,
  context: Context,
  modifier: Modifier = Modifier,
  setup: com.example.data.api.SetupDto? = null
) {
  NextDnsCard(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_af80266dc2),
    subtitle = AppStrings.get(R.string.ui_9e84007b0d)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        .clip(RoundedCornerShape(14.dp))
    ) {
      EndpointTableRow("ID", profileId) { copyToClipboard(context, profileId, "ID") }
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      EndpointTableRow("DNS-over-TLS/QUIC", "$profileId.dns.nextdns.io") {
        copyToClipboard(context, "$profileId.dns.nextdns.io", AppStrings.get(R.string.copy_dot))
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      EndpointTableRow("DNS-over-HTTPS", "https://dns.nextdns.io/$profileId") {
        copyToClipboard(context, "https://dns.nextdns.io/$profileId", AppStrings.get(R.string.copy_doh))
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      val ipv6 = setupIpv6Addresses(setup)
      EndpointTableRow("IPv6", ipv6.joinToString("\n").ifEmpty { "—" }, canCopy = ipv6.isNotEmpty()) {
        copyToClipboard(context, ipv6.joinToString("\n"), "IPv6")
      }
    }
  }
}

@Composable
private fun LinkedIpSection(
  profileId: String,
  setup: com.example.data.api.SetupDto?,
  testResult: DiagnosticTestResult,
  showAdvancedIpSettings: Boolean,
  onToggleAdvanced: () -> Unit,
  onLinkIp: () -> Unit,
  context: Context,
  modifier: Modifier = Modifier
) {
  val detectedIp = testResult.clientIp.ifBlank { "" }
  val rawStatus = testResult.status.lowercase().trim()
  val isUsingNextDns = rawStatus == "ok" || rawStatus == "using-nextdns"

  NextDnsCard(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_e1ba21dcd4),
    subtitle = AppStrings.get(R.string.ui_8ddbc3a609)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        .clip(RoundedCornerShape(14.dp))
    ) {
      val ipv4List = setup?.ipv4 ?: listOf("45.90.28.234", "45.90.30.234")
      EndpointTableRow(AppStrings.get(R.string.ui_e0b841095a), ipv4List.joinToString("\n")) {
        copyToClipboard(context, ipv4List.firstOrNull() ?: "", AppStrings.get(R.string.copy_dns))
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = AppStrings.get(R.string.ui_e1ba21dcd4),
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.width(110.dp)
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = detectedIp.ifBlank { AppStrings.get(R.string.ui_d2b13bfcc8) },
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .heightIn(min = 48.dp)
              .padding(horizontal = 4.dp)
              .bounceClick(scaleDown = 0.99f) {
                if (detectedIp.isNotBlank()) {
                  copyToClipboard(context, detectedIp, "IP Adresi")
                }
              }
          )
          if (isUsingNextDns && detectedIp.isNotBlank()) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = AppStrings.get(R.string.ui_fb8d508c13),
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(16.dp)
            )
          } else {
            Surface(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .heightIn(min = 48.dp)
                .bounceClick(scaleDown = 0.98f, onClick = onLinkIp),
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Link,
                  contentDescription = AppStrings.get(R.string.ui_6166e7d8f7),
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(13.dp)
                )
                Text(
                  text = AppStrings.get(R.string.ui_6166e7d8f7),
                  color = MaterialTheme.colorScheme.primary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .heightIn(min = 48.dp)
        .bounceClick(scaleDown = 0.98f, onClick = onToggleAdvanced)
        .padding(vertical = 6.dp, horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = if (showAdvancedIpSettings) AppStrings.get(R.string.ui_e351e2362e) else AppStrings.get(R.string.ui_533a6281d8),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
      )
      Icon(
        imageVector = if (showAdvancedIpSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp)
      )
    }

    if (showAdvancedIpSettings) {
      Spacer(modifier = Modifier.height(8.dp))
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(AppStrings.get(R.string.ui_4dc480c54f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
          Text(
            text = "https://link-ip.nextdns.io/$profileId/update",
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .heightIn(min = 48.dp)
              .padding(horizontal = 4.dp)
              .bounceClick(scaleDown = 0.99f) {
                copyToClipboard(context, "https://link-ip.nextdns.io/$profileId/update", AppStrings.get(R.string.copy_ddns))
              }
          )
        }
      }
    }
  }
}

@Composable
private fun SetupGuideSection(
  profileId: String,
  setup: com.example.data.api.SetupDto?,
  platforms: List<String>,
  selectedPlatform: String,
  onPlatformSelected: (String) -> Unit,
  context: Context,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_917d229d50),
    subtitle = AppStrings.get(R.string.ui_cbba6dfad2)
  ) {
    val platformScroll = rememberScrollState()
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(platformScroll)
        .padding(bottom = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      platforms.forEach { plat ->
        val isPlatSelected = selectedPlatform == plat
        Surface(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .heightIn(min = 48.dp)
            .bounceClick(scaleDown = 0.98f) { onPlatformSelected(plat) },
          shape = RoundedCornerShape(12.dp),
          color = if (isPlatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
          border = BorderStroke(
            1.dp,
            if (isPlatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
          )
        ) {
          Text(
            text = plat,
            color = if (isPlatSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (isPlatSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
          )
        }
      }
    }

    when (selectedPlatform) {
      "Android" -> AndroidSetupGuide(profileId, context)
      "Windows" -> WindowsSetupGuide(profileId, setup, context)
      "iOS" -> IosSetupGuide(profileId, context)
      "macOS" -> MacOsSetupGuide(profileId, context)
      "Linux" -> LinuxSetupGuide(profileId, context)
      "ChromeOS" -> ChromeOsSetupGuide(profileId, context)
      AppStrings.get(R.string.ui_4b4de7fb15) -> BrowserSetupGuide(profileId, context)
      AppStrings.get(R.string.ui_7b5d49c4a6) -> RouterSetupGuide(profileId, setup, context)
      else -> AndroidSetupGuide(profileId, context)
    }
  }
}

// =========================================================================
// Platform Setup Guides
// =========================================================================

@Composable
private fun EndpointTableRow(
  label: String,
  value: String,
  canCopy: Boolean = true,
  onCopy: () -> Unit
) {
  var isCopied by remember { mutableStateOf(false) }

  LaunchedEffect(isCopied) {
    if (isCopied) {
      delay(2000L)
      isCopied = false
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .bounceClick(scaleDown = 0.99f, enabled = canCopy) {
        if (canCopy) {
          onCopy()
          isCopied = true
        }
      }
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 12.5.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.width(130.dp)
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = value.ifEmpty { "—" },
        color = if (isCopied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.weight(1f)
      )
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isCopied) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(
          1.dp,
          if (isCopied) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
          else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
          Icon(
            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = AppStrings.get(R.string.ui_a8bcca42d9),
            tint = if (isCopied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp)
          )
          Text(
            text = if (isCopied) AppStrings.get(R.string.ui_02af74b2f0) else AppStrings.get(R.string.ui_a8bcca42d9),
            color = if (isCopied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun CopyableValueBox(
  value: String,
  context: Context,
  modifier: Modifier = Modifier,
  label: String? = null,
  copyLabel: String = AppStrings.get(R.string.ui_4cfa2b03d5),
  description: String? = null,
  isCode: Boolean = true
) {
  var isCopied by remember { mutableStateOf(false) }

  LaunchedEffect(isCopied) {
    if (isCopied) {
      delay(2000L)
      isCopied = false
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    if (!label.isNullOrBlank()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = label,
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold
        )
        if (!description.isNullOrBlank()) {
          Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
          )
        }
      }
    }

    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(
        1.dp,
        if (isCopied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
      ),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .bounceClick(scaleDown = 0.99f, enabled = value.isNotBlank()) {
          if (value.isNotBlank()) {
            copyToClipboard(context, value, copyLabel)
            isCopied = true
          }
        }
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = value.ifEmpty { "—" },
          color = if (isCopied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
          fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier
            .weight(1f)
            .padding(end = 10.dp)
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (isCopied) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                  else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
          border = BorderStroke(
            1.dp,
            if (isCopied) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
          )
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
              contentDescription = AppStrings.get(R.string.ui_a8bcca42d9),
              tint = if (isCopied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(13.dp)
            )
            Text(
              text = if (isCopied) AppStrings.get(R.string.ui_02af74b2f0) else AppStrings.get(R.string.ui_a8bcca42d9),
              color = if (isCopied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SetupStepItem(num: String, text: String, subtext: String? = null) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
    modifier = Modifier.fillMaxWidth()
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
      modifier = Modifier.size(24.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = num,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp
        )
      }
    }
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 17.sp
      )
      if (!subtext.isNullOrBlank()) {
        Text(
          text = subtext,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.5.sp,
          lineHeight = 15.sp
        )
      }
    }
  }
}

@Composable
private fun AndroidSetupGuide(profileId: String, context: Context) {
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NextDnsRecommendedBadge()
          Text(AppStrings.get(R.string.private_dns_dot), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
        Text(
          text = AppStrings.get(R.string.ui_4cd6aa49be),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp,
          lineHeight = 16.sp
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

        SetupStepItem("1", AppStrings.get(R.string.ui_b7ebc7a832))
        SetupStepItem("2", AppStrings.get(R.string.ui_2335163b7a))
        SetupStepItem("3", AppStrings.get(R.string.ui_57d715d68f))
        SetupStepItem("4", AppStrings.get(R.string.ui_92e7dadb33))

        CopyableValueBox(
          label = AppStrings.get(R.string.ui_523d78e571),
          value = "$profileId.dns.nextdns.io",
          context = context,
          copyLabel = AppStrings.get(R.string.ui_66bf5a8c78)
        )

        // Pro tip: Cihaz adlandırma
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
              Text(AppStrings.get(R.string.ui_138d8972ec), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(
              text = AppStrings.get(R.string.named_device_dot, profileId),
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 15.sp
            )
            CopyableValueBox(
              value = "telefon-$profileId.dns.nextdns.io",
              context = context,
              copyLabel = AppStrings.get(R.string.ui_83ad857e6d)
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        NextDnsButton(
          text = AppStrings.get(R.string.ui_57efebc018),
          onClick = {
            try {
              context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (_: Exception) {
              try {
                context.startActivity(Intent("android.settings.NETWORK_PROVIDER_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
              } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
              }
            }
          },
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
private fun WindowsSetupGuide(profileId: String, setup: com.example.data.api.SetupDto?, context: Context) {
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NextDnsRecommendedBadge()
          Text(AppStrings.get(R.string.ui_1750d7455f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
        SetupStepItem("1", AppStrings.get(R.string.ui_f98df79775))
        SetupStepItem("2", AppStrings.get(R.string.ui_c4b669d5a4))
        SetupStepItem("3", AppStrings.get(R.string.ui_b8b91eeb7d))
        SetupStepItem("4", AppStrings.get(R.string.ui_f817bdb64b))

        val ipv4First = setup?.ipv4?.firstOrNull() ?: "45.90.28.0"
        CopyableValueBox(
          label = AppStrings.get(R.string.ui_91f537e633),
          value = ipv4First,
          context = context,
          copyLabel = AppStrings.get(R.string.ui_6bd15f6947)
        )

        SetupStepItem("5", AppStrings.get(R.string.ui_c566947646))
        SetupStepItem("6", AppStrings.get(R.string.ui_8107916865))

        CopyableValueBox(
          label = AppStrings.get(R.string.ui_2483d932cf),
          value = "https://dns.nextdns.io/$profileId",
          context = context,
          copyLabel = AppStrings.get(R.string.ui_f74994ccb0)
        )
      }
    }
  }
}

@Composable
private fun IosSetupGuide(profileId: String, context: Context) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NextDnsRecommendedBadge()
          Text(AppStrings.get(R.string.ui_fab118a38d), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
        Text(
          text = AppStrings.get(R.string.ui_987c9f2614),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp,
          lineHeight = 16.sp
        )

        SetupStepItem("1", AppStrings.get(R.string.ui_ba08e4a4e3))

        CopyableValueBox(
          label = AppStrings.get(R.string.ui_2a7a58df0b),
          value = "https://apple.nextdns.io/$profileId",
          context = context,
          copyLabel = AppStrings.get(R.string.ui_c7c580fb51)
        )

        SetupStepItem("2", AppStrings.get(R.string.ui_a02ffecee5))
        SetupStepItem("3", AppStrings.get(R.string.ui_d8a134c345))

        NextDnsButton(
          text = AppStrings.get(R.string.ui_2a3846bca7),
          onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://apple.nextdns.io/$profileId")).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
          },
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
private fun MacOsSetupGuide(profileId: String, context: Context) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(AppStrings.get(R.string.ui_f7f97af3a5), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
      Text(
        text = AppStrings.get(R.string.ui_e667cd78cb),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 16.sp
      )

      SetupStepItem("1", AppStrings.get(R.string.ui_b48a2108af))

      CopyableValueBox(
        label = AppStrings.get(R.string.ui_aea515d586),
        value = "https://apple.nextdns.io/$profileId",
        context = context,
        copyLabel = AppStrings.get(R.string.ui_c7c580fb51)
      )

      SetupStepItem("2", AppStrings.get(R.string.ui_cd8a56e83b))

      SetupStepItem("3", AppStrings.get(R.string.ui_fbe5a8bd84))

      CopyableValueBox(
        label = AppStrings.get(R.string.ui_b9d9ff28ca),
        value = "brew install nextdns/tap/nextdns && sudo nextdns install -config $profileId -auto-activate",
        context = context,
        copyLabel = AppStrings.get(R.string.ui_b9d9ff28ca)
      )
    }
  }
}

@Composable
private fun LinuxSetupGuide(profileId: String, context: Context) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(AppStrings.get(R.string.ui_8630eade46), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
      Text(
        text = AppStrings.get(R.string.ui_de99415fe0),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 16.sp
      )

      SetupStepItem("1", AppStrings.get(R.string.ui_e0deb2f485))

      CopyableValueBox(
        label = AppStrings.get(R.string.ui_aa7806f142),
        value = "sh -c 'sh -c \"$(curl -sL https://nextdns.io/install)\"'",
        context = context,
        copyLabel = AppStrings.get(R.string.ui_6c7334e6d4)
      )

      SetupStepItem("2", AppStrings.get(R.string.ui_b2b5e135ac))

      CopyableValueBox(
        label = AppStrings.get(R.string.ui_69209827d4),
        value = profileId,
        context = context,
        copyLabel = AppStrings.get(R.string.ui_ed46ef486b)
      )

      SetupStepItem("3", AppStrings.get(R.string.ui_f8394d404e))

      CopyableValueBox(
        label = AppStrings.get(R.string.ui_7986009971),
        value = "sudo nextdns start",
        context = context,
        copyLabel = AppStrings.get(R.string.ui_7986009971)
      )
    }
  }
}

@Composable
private fun ChromeOsSetupGuide(profileId: String, context: Context) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(AppStrings.get(R.string.ui_1e14b1c274), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
      SetupStepItem("1", AppStrings.get(R.string.ui_6193571a24))
      SetupStepItem("2", AppStrings.get(R.string.ui_db6ba771e0))

      CopyableValueBox(
        label = AppStrings.get(R.string.ui_5de4f38861),
        value = "https://dns.nextdns.io/$profileId",
        context = context,
        copyLabel = AppStrings.get(R.string.copy_chromeos_doh)
      )
    }
  }
}

@Composable
private fun BrowserSetupGuide(profileId: String, context: Context) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(AppStrings.get(R.string.ui_41f0468d9e), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
      Text(
        text = AppStrings.get(R.string.ui_8fa4a6d58b),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 16.sp
      )

      // Chrome / Brave / Edge
      Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Google Chrome / Brave / Edge", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
          Text(
            text = AppStrings.get(R.string.ui_4f8d04c22f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 15.sp
          )
          CopyableValueBox(
            value = "https://dns.nextdns.io/$profileId",
            context = context,
            copyLabel = AppStrings.get(R.string.ui_d5aa6b5ec2)
          )
        }
      }

      // Firefox
      Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Mozilla Firefox", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
          Text(
            text = AppStrings.get(R.string.ui_715efd7b23),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 15.sp
          )
          CopyableValueBox(
            value = "https://dns.nextdns.io/$profileId",
            context = context,
            copyLabel = AppStrings.get(R.string.ui_90834f5514)
          )
        }
      }
    }
  }
}

@Composable
private fun RouterSetupGuide(profileId: String, setup: com.example.data.api.SetupDto?, context: Context) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(AppStrings.get(R.string.ui_f62318dd8b), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
      Text(
        text = AppStrings.get(R.string.router_description),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 16.sp
      )

      Text(AppStrings.get(R.string.ui_9de822673b), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)

      val ipv4First = setup?.ipv4?.getOrNull(0) ?: "45.90.28.234"
      CopyableValueBox(
        label = AppStrings.get(R.string.ui_e074fca8cf),
        value = ipv4First,
        context = context,
        copyLabel = AppStrings.get(R.string.ui_e170a66b88)
      )

      val ipv4Second = setup?.ipv4?.getOrNull(1) ?: "45.90.30.234"
      CopyableValueBox(
        label = AppStrings.get(R.string.ui_2e8400bb4b),
        value = ipv4Second,
        context = context,
        copyLabel = AppStrings.get(R.string.ui_f312681126)
      )

      Text(AppStrings.get(R.string.ui_6cda839fb5), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)

      val ipv6First = setupIpv6Addresses(setup).getOrNull(0).orEmpty()
      CopyableValueBox(
        label = AppStrings.get(R.string.ui_4eb25410e1),
        value = ipv6First,
        context = context,
        copyLabel = AppStrings.get(R.string.ui_e086835b9a)
      )

      val ipv6Second = setupIpv6Addresses(setup).getOrNull(1).orEmpty()
      CopyableValueBox(
        label = AppStrings.get(R.string.ui_2dbf7664c8),
        value = ipv6Second,
        context = context,
        copyLabel = AppStrings.get(R.string.ui_6431ef9198)
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

      Text(AppStrings.get(R.string.ui_48f50e5ec4), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)
      Text(
        text = AppStrings.get(R.string.ui_ffafd0b3a7),
        fontSize = 11.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 15.sp
      )

      CopyableValueBox(
        label = AppStrings.get(R.string.ui_a7c4020d15),
        value = "sh -c 'sh -c \"$(curl -sL https://nextdns.io/install)\"'",
        context = context,
        copyLabel = AppStrings.get(R.string.ui_ebf16194a7)
      )
    }
  }
}
