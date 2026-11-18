package com.example.ui.screens

import com.example.i18n.UiLabels

import com.example.R
import com.example.i18n.AppStrings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AnalyticsSummary
import com.example.data.model.AnalyticsSeriesQuery
import com.example.data.model.SeriesMetric
import com.example.data.model.DeviceLabelResolver
import com.example.data.model.DeviceMetric
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NextDnsViewModel
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant

@Composable
fun AnalyticsScreen(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
  val analytics by viewModel.analytics.collectAsStateWithLifecycle()
  val analyticsLastSuccessAt by viewModel.analyticsLastSuccessAt.collectAsStateWithLifecycle()
  val analyticsErrorMessage by viewModel.analyticsErrorMessage.collectAsStateWithLifecycle()
  val isAnalyticsLoading by viewModel.isAnalyticsLoading.collectAsStateWithLifecycle()
  val analyticsSeries by viewModel.analyticsSeries.collectAsStateWithLifecycle()

  var selectedDeviceFilter by remember { mutableStateOf<String?>(null) }
  var selectedTimeFilter by remember { mutableStateOf("24h") }
  var selectedSeriesMetric by remember { mutableStateOf(SeriesMetric.STATUS) }
  var selectedDomainStatus by remember { mutableStateOf<String?>(null) }
  var rootGrouped by remember { mutableStateOf(false) }
  val seriesRange = remember(selectedTimeFilter) {
    val to = Instant.now()
    val duration = when (selectedTimeFilter) {
      "24h" -> Duration.ofHours(24)
      "7d" -> Duration.ofDays(7)
      "30d" -> Duration.ofDays(30)
      else -> Duration.ofDays(90)
    }
    val intervalSeconds = if (duration <= Duration.ofHours(24)) 3_600L else 86_400L
    Triple(to.minus(duration), to, intervalSeconds)
  }

  val lifecycleOwner = LocalLifecycleOwner.current

  LaunchedEffect(activeProfile?.id) {
    viewModel.refreshAnalytics(selectedDeviceFilter, selectedTimeFilter)
  }

  LaunchedEffect(activeProfile?.id, selectedDeviceFilter, selectedTimeFilter, selectedSeriesMetric, selectedDomainStatus, rootGrouped) {
    val profileId = activeProfile?.id?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
    viewModel.refreshAnalyticsSeries(
      AnalyticsSeriesQuery(
        profileId = profileId,
        deviceId = selectedDeviceFilter,
        fromInclusive = seriesRange.first,
        toExclusive = seriesRange.second,
        metric = selectedSeriesMetric,
        intervalSeconds = seriesRange.third,
        domainStatus = selectedDomainStatus.takeIf { selectedSeriesMetric == SeriesMetric.DOMAINS },
        root = rootGrouped && selectedSeriesMetric == SeriesMetric.DOMAINS
      )
    )
  }

  DisposableEffect(lifecycleOwner, activeProfile?.id) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          viewModel.refreshAnalytics(selectedDeviceFilter, selectedTimeFilter)
          viewModel.startAnalyticsPolling()
        }
        Lifecycle.Event.ON_PAUSE -> viewModel.stopAnalyticsPolling()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)

    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      viewModel.startAnalyticsPolling()
    }

    onDispose {
      viewModel.stopAnalyticsPolling()
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  var showDeviceFilterMenu by remember { mutableStateOf(false) }
  var showTimeFilterMenu by remember { mutableStateOf(false) }

  val numFormat = remember(AppStrings.locale) { NumberFormat.getInstance(AppStrings.locale) }
  val timesList = listOf("24h", "7d", "30d", "90d")

  val devicesList = remember(analytics.topDevices) {
    analytics.topDevices.filter { it.id.isNotBlank() }.distinctBy { it.id }
  }

  val contentAlpha by animateFloatAsState(
    targetValue = if (isAnalyticsLoading) 0.65f else 1f,
    label = "analytics_loading_alpha"
  )

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag(if (!isAnalyticsLoading && analyticsLastSuccessAt != null &&
        !analyticsSeries.loading && analyticsSeries.rows.isNotEmpty()) "analytics_ready" else "analytics_loading")
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item(key = "filters", contentType = "filters") {
      AnalyticsFilterBar(
        selectedDeviceFilter = selectedDeviceFilter,
        devicesList = devicesList,
        showDeviceFilterMenu = showDeviceFilterMenu,
        onToggleDeviceFilterMenu = { showDeviceFilterMenu = it },
        onSelectDevice = {
          selectedDeviceFilter = it
          viewModel.refreshAnalytics(it, selectedTimeFilter)
        },
        selectedTimeFilter = selectedTimeFilter,
        timesList = timesList,
        showTimeFilterMenu = showTimeFilterMenu,
        onToggleTimeFilterMenu = { showTimeFilterMenu = it },
        onSelectTime = {
          selectedTimeFilter = it
          viewModel.refreshAnalytics(selectedDeviceFilter, it)
        },
        isLoading = isAnalyticsLoading
      )
    }

    if (analyticsLastSuccessAt != null && !analyticsErrorMessage.isNullOrBlank()) {
      item(key = "partial-warning", contentType = "partial-warning") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("analytics_partial_warning"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = analyticsErrorMessage.orEmpty(),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 12.5.sp
          )
        }
      }
    }

    if (analyticsLastSuccessAt == null) {
      item(key = "initial-state", contentType = "initial-state") {
        NextDnsCard(
          title = AppStrings.get(R.string.ui_dfe06b6d3d),
          subtitle = AppStrings.get(R.string.ui_940a3407df)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (isAnalyticsLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
              )
            } else {
              Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
            }
            Text(
              text = when {
                isAnalyticsLoading -> AppStrings.get(R.string.ui_f227ea13fc)
                !analyticsErrorMessage.isNullOrBlank() -> analyticsErrorMessage!!
                else -> AppStrings.get(R.string.ui_fa93b503d4)
              },
              color = if (!analyticsErrorMessage.isNullOrBlank() && !isAnalyticsLoading) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
              fontSize = 12.5.sp
            )
          }
        }
      }
    } else {
    item(key = "overview", contentType = "overview") {
      Box(modifier = Modifier.alpha(contentAlpha)) {
        AnalyticsOverviewCards(
          analytics = analytics,
          numFormat = numFormat
        )
      }
    }

    item(key = "resolved-domains", contentType = "resolved-domains") {
      Box(modifier = Modifier.alpha(contentAlpha)) {
        ResolvedDomainsCard(
          analytics = analytics,
          numFormat = numFormat
        )
      }
    }

    item(key = "blocked-domains", contentType = "blocked-domains") {
      Box(modifier = Modifier.alpha(contentAlpha)) {
        BlockedDomainsCard(
          analytics = analytics,
          numFormat = numFormat
        )
      }
    }

    item(key = "blocked-reasons", contentType = "blocked-reasons") {
      Box(modifier = Modifier.alpha(contentAlpha)) {
        BlockedReasonsCard(
          analytics = analytics,
          numFormat = numFormat
        )
      }
    }

    item(key = "devices", contentType = "devices") {
      Box(modifier = Modifier.alpha(contentAlpha)) {
        DevicesAnalyticsCard(
          analytics = analytics,
          numFormat = numFormat
        )
      }
    }

    if (analytics.topIps.isNotEmpty()) {
      item(key = "ips", contentType = "ips") {
        Box(modifier = Modifier.alpha(contentAlpha)) {
          AnalyticsIpsCard(analytics = analytics, numFormat = numFormat)
        }
      }
    }

    item(key = "root-domains", contentType = "root-domains") {
      Box(modifier = Modifier.alpha(contentAlpha)) {
        RootDomainsCard(
          analytics = analytics,
          numFormat = numFormat
        )
      }
    }

    item(key = "gafam", contentType = "gafam") {
      Box(modifier = Modifier.alpha(contentAlpha)) {
        GafamDominanceCard(
          analytics = analytics,
          numFormat = numFormat
        )
      }
    }

    item(key = "encryption", contentType = "encryption") {
      Box(modifier = Modifier.alpha(contentAlpha)) {
        EncryptedDnsAndDnssecCard(
          analytics = analytics
        )
      }
    }

    item(key = "destinations", contentType = "destinations") {
      Box(modifier = Modifier.alpha(contentAlpha)) {
        TrafficDestinationsCard(
          analytics = analytics
        )
      }
    }

    item(key = "series", contentType = "series") {
      AnalyticsSeriesChart(
        state = analyticsSeries,
        selectedMetric = selectedSeriesMetric,
        domainStatus = selectedDomainStatus,
        rootGrouped = rootGrouped,
        onMetricSelected = { selectedSeriesMetric = it },
        onDomainStatusSelected = { selectedDomainStatus = it },
        onRootGroupedChanged = { rootGrouped = it }
      )
    }

    }
  }
}

// =========================================================================
// Modular Composable Sections
// =========================================================================

@Composable
private fun AnalyticsDomainIcon(domain: String) {
  Box(
    modifier = Modifier
      .size(18.dp)
      .clip(RoundedCornerShape(4.dp))
      .testTag("analytics_domain_favicon")
  ) {
    FaviconImage(domain = domain, modifier = Modifier.fillMaxSize())
  }
}

@Composable
private fun AnalyticsFilterBar(
  selectedDeviceFilter: String?,
  devicesList: List<DeviceMetric>,
  showDeviceFilterMenu: Boolean,
  onToggleDeviceFilterMenu: (Boolean) -> Unit,
  onSelectDevice: (String?) -> Unit,
  selectedTimeFilter: String,
  timesList: List<String>,
  showTimeFilterMenu: Boolean,
  onToggleTimeFilterMenu: (Boolean) -> Unit,
  onSelectTime: (String) -> Unit,
  isLoading: Boolean = false,
  modifier: Modifier = Modifier
) {
  val selectedDeviceLabel = if (selectedDeviceFilter == null) {
    AppStrings.get(R.string.all_devices)
  } else {
    devicesList.firstOrNull { it.id == selectedDeviceFilter }?.let { device ->
      DeviceLabelResolver.resolve(
        device.name, device.model, device.id, AppStrings.get(R.string.device_unidentified)
      )
    } ?: AppStrings.get(R.string.device_unidentified)
  }
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box {
      Surface(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
          .testTag("analytics_device_filter")
          .bounceClick { onToggleDeviceFilterMenu(true) },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(selectedDeviceLabel, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
          Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
      }
      DropdownMenu(
        expanded = showDeviceFilterMenu,
        onDismissRequest = { onToggleDeviceFilterMenu(false) },
        modifier = Modifier
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
      ) {
        DropdownMenuItem(
          modifier = Modifier.testTag("analytics_device_option_all"),
          text = {
            Text(
              AppStrings.get(R.string.all_devices),
              color = if (selectedDeviceFilter == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              fontSize = 12.sp
            )
          },
          onClick = {
            onSelectDevice(null)
            onToggleDeviceFilterMenu(false)
          }
        )
        devicesList.forEach { dev ->
          val deviceLabel = DeviceLabelResolver.resolve(
            dev.name, dev.model, dev.id, AppStrings.get(R.string.device_unidentified)
          )
          DropdownMenuItem(
            modifier = Modifier.testTag("analytics_device_option_${dev.id}"),
            text = {
              Text(
                deviceLabel,
                color = if (dev.id == selectedDeviceFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
              )
            },
            onClick = {
              onSelectDevice(dev.id)
              onToggleDeviceFilterMenu(false)
            }
          )
        }
      }
    }

    Box {
      Surface(
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
          .testTag("analytics_time_filter")
          .bounceClick { onToggleTimeFilterMenu(true) },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(analyticsTimeFilterLabel(selectedTimeFilter), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
          Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
      }
      DropdownMenu(
        expanded = showTimeFilterMenu,
        onDismissRequest = { onToggleTimeFilterMenu(false) },
        modifier = Modifier
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
      ) {
        timesList.forEach { tm ->
          DropdownMenuItem(
            modifier = Modifier.testTag("analytics_time_option_$tm"),
            text = { Text(analyticsTimeFilterLabel(tm), color = if (tm == selectedTimeFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) },
            onClick = {
              onSelectTime(tm)
              onToggleTimeFilterMenu(false)
            }
          )
        }
      }
    }

    if (isLoading) {
      CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary
      )
    }
  }
}

@Composable
private fun analyticsTimeFilterLabel(filter: String): String = AppStrings.get(
  when (filter) {
    "24h" -> R.string.last_24_hours
    "7d" -> R.string.last_7_days
    "90d" -> R.string.last_3_months
    else -> R.string.last_30_days
  }
)

@Composable
private fun AnalyticsOverviewCards(
  analytics: AnalyticsSummary,
  numFormat: NumberFormat,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Surface(
      modifier = Modifier
        .weight(1f)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(14.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = numFormat.format(analytics.totalQueries),
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
          ),
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(AppStrings.get(R.string.queries), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, maxLines = 1)
      }
    }

    Surface(
      modifier = Modifier
        .weight(1f)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(14.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = numFormat.format(analytics.blockedQueries),
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
          ),
          color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(AppStrings.get(R.string.blocked), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, maxLines = 1)
      }
    }

    Surface(
      modifier = Modifier
        .weight(1f)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(14.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = AppStrings.percent(analytics.blockRate.coerceIn(0.0, 100.0)),
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
          ),
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(AppStrings.get(R.string.ui_4f143875ca), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, maxLines = 1)
      }
    }
  }
}

@Composable
private fun ResolvedDomainsCard(
  analytics: AnalyticsSummary,
  numFormat: NumberFormat,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    title = AppStrings.get(R.string.ui_b13fa1ff43),
    modifier = modifier.testTag("analytics_resolved_domains_card")
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      analytics.topAllowedDomains.take(6).forEach { dom ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(16.dp)
            )
            AnalyticsDomainIcon(dom.domain)
            Text(
              text = dom.domain,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
          Text(
            text = numFormat.format(dom.queries),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun BlockedDomainsCard(
  analytics: AnalyticsSummary,
  numFormat: NumberFormat,
  modifier: Modifier = Modifier
) {
  NextDnsCard(title = AppStrings.get(R.string.ui_c98fb72576), modifier = modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      analytics.topBlockedDomains.take(6).forEach { dom ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Default.Block,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp)
            )
            AnalyticsDomainIcon(dom.domain)
            Text(
              text = dom.domain,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
          Text(
            text = numFormat.format(dom.queries),
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun BlockedReasonsCard(
  analytics: AnalyticsSummary,
  numFormat: NumberFormat,
  modifier: Modifier = Modifier
) {
  NextDnsCard(title = AppStrings.get(R.string.ui_74b2548cbb), modifier = modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      analytics.topBlockedReasons.forEach { (reason, count) ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = reason,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = numFormat.format(count),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun DevicesAnalyticsCard(
  analytics: AnalyticsSummary,
  numFormat: NumberFormat,
  modifier: Modifier = Modifier
) {
  NextDnsCard(title = AppStrings.get(R.string.ui_238a17ed89), modifier = modifier.testTag("analytics_devices_card")) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      analytics.topDevices.forEach { dev ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Default.Devices,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = DeviceLabelResolver.resolve(
                dev.name,
                dev.model,
                dev.id,
                AppStrings.get(R.string.device_unidentified)
              ),
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
          Text(
            text = numFormat.format(dev.queries),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun RootDomainsCard(
  analytics: AnalyticsSummary,
  numFormat: NumberFormat,
  modifier: Modifier = Modifier
) {
  NextDnsCard(title = AppStrings.get(R.string.ui_ff7847fdd3), modifier = modifier) {
    if (analytics.topDomains.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
        Text(AppStrings.get(R.string.ui_00d38c0168), color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        analytics.topDomains.take(6).forEach { dom ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              AnalyticsDomainIcon(dom.domain)
              Text(
                text = dom.domain,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Text(
              text = numFormat.format(dom.queries),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AnalyticsIpsCard(
  analytics: AnalyticsSummary,
  numFormat: NumberFormat,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    title = AppStrings.get(R.string.analytics_ips_title),
    modifier = modifier.testTag("analytics_ips_card")
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      analytics.topIps.take(6).forEachIndexed { index, ip ->
        val location = listOfNotNull(ip.city?.takeIf(String::isNotBlank), ip.country?.takeIf(String::isNotBlank))
          .joinToString(", ")
        val network = listOfNotNull(
          ip.isp?.takeIf(String::isNotBlank),
          ip.asn?.let { "AS$it" }
        ).joinToString(" · ")
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("analytics_ip_row_$index"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).testTag("analytics_ip_icon_$index")
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = ip.ip,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (location.isNotBlank()) {
              Text(location, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            }
            if (network.isNotBlank()) {
              Text(network, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
            }
          }
          Text(
            text = numFormat.format(ip.queries),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

@Composable
private fun GafamDominanceCard(
  analytics: AnalyticsSummary,
  numFormat: NumberFormat,
  modifier: Modifier = Modifier
) {
  val segments = remember(analytics) { GafamChartModel.from(analytics) }
  val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
  val palette = remember(darkTheme) {
    if (darkTheme) {
      mapOf(
        GafamCompany.GOOGLE to Color(0xFF56B4E9),
        GafamCompany.FACEBOOK to Color(0xFFE58CC4),
        GafamCompany.MICROSOFT to Color(0xFF48CFA6),
        GafamCompany.APPLE to Color(0xFFD1D1D1),
        GafamCompany.AMAZON to Color(0xFFFFCA66),
        GafamCompany.OTHER to Color(0xFF858B94)
      )
    } else {
      mapOf(
        GafamCompany.GOOGLE to Color(0xFF0072B2),
        GafamCompany.FACEBOOK to Color(0xFFCC79A7),
        GafamCompany.MICROSOFT to Color(0xFF009E73),
        GafamCompany.APPLE to Color(0xFF4D4D4D),
        GafamCompany.AMAZON to Color(0xFFE69F00),
        GafamCompany.OTHER to Color(0xFF9AA0A6)
      )
    }
  }
  val localizedSegmentLabels = segments.map { segment ->
    AppStrings.get(
      R.string.gafam_company_stats,
      gafamDisplayName(segment.company),
      AppStrings.get(
        R.string.percentage_queries,
        AppStrings.percent(segment.sharePercent),
        numFormat.format(segment.queries)
      )
    )
  }
  val accessibilityDescription = AppStrings.get(
    R.string.gafam_chart_accessibility,
    localizedSegmentLabels.joinToString(AppStrings.get(R.string.gafam_accessibility_separator))
  )

  NextDnsCard(
    title = AppStrings.get(R.string.ui_8f4d0d2695),
    subtitle = AppStrings.get(R.string.gafam_description),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        Canvas(
          modifier = Modifier
            .fillMaxSize()
            .testTag("gafam_chart")
            .semantics { contentDescription = accessibilityDescription }
        ) {
          val stroke = 18.dp.toPx()
          var startAngle = -90f
          segments.forEach { segment ->
            val sweep = (segment.sharePercent / 100.0 * 360.0).toFloat().coerceIn(0f, 360f)
            if (sweep > 0f) {
              drawArc(
                color = palette.getValue(segment.company),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = stroke)
              )
              startAngle += sweep
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(16.dp))

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEachIndexed { index, segment ->
          val dotColor = palette.getValue(segment.company)
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Text(
              text = localizedSegmentLabels[index],
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 11.5.sp,
              modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = localizedSegmentLabels[index]
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun gafamDisplayName(company: GafamCompany): String = when (company) {
  GafamCompany.OTHER -> AppStrings.get(R.string.ui_36ed682fbd)
  else -> company.apiName
}

@Composable
private fun EncryptedDnsAndDnssecCard(
  analytics: AnalyticsSummary,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    NextDnsCard(title = AppStrings.get(R.string.ui_40dcde1fc7)) {
      Text(
        text = AppStrings.get(R.string.ui_f58c928dd1),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 12.dp)
      )
      Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outline)) {
        val encPct = (analytics.encryptedDnsPercentage / 100f).coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxWidth(encPct).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
      }
      Text(
        text = AppStrings.percent(analytics.encryptedDnsPercentage),
        color = MaterialTheme.colorScheme.tertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    NextDnsCard(title = AppStrings.get(R.string.ui_37eafaf79e)) {
      Text(
        text = AppStrings.get(R.string.ui_b5d24e85ce),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 12.dp)
      )
      Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outline)) {
        val secPct = (analytics.dnssecPercentage / 100f).coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxWidth(secPct).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
      }
      Text(
        text = AppStrings.percent(analytics.dnssecPercentage),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp)
      )
    }
  }
}

@Composable
private fun TrafficDestinationsCard(
  analytics: AnalyticsSummary,
  modifier: Modifier = Modifier
) {
  NextDnsCard(title = AppStrings.get(R.string.ui_eb85097b6a), modifier = modifier) {
    Text(
      text = AppStrings.get(R.string.ui_df2660a8a1),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 11.sp,
      modifier = Modifier.padding(bottom = 12.dp)
    )
    WorldMapChart(countryData = analytics.topCountries)
  }
}
