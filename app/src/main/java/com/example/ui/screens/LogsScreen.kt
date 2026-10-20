package com.example.ui.screens

import com.example.i18n.UiLabels
import com.example.R
import com.example.i18n.AppStrings

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DeviceMetric
import com.example.data.model.DeviceLabelResolver
import com.example.data.model.DnsLogEntry
import com.example.data.model.NEXTDNS_UNIDENTIFIED_DEVICE_ID
import com.example.data.repository.LogsPaginationState
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NextDnsViewModel
import kotlinx.coroutines.delay
import java.time.Instant

fun formatRelativeTime(timestampStr: String): String {
  try {
    if (timestampStr.isBlank()) return AppStrings.get(R.string.ui_68af8fa298)
    if (timestampStr.endsWith(AppStrings.get(R.string.ui_16ef24fc3b)) || timestampStr == AppStrings.get(R.string.ui_68af8fa298)) return timestampStr
    val time = if (timestampStr.contains("T")) {
      try {
        Instant.parse(timestampStr).toEpochMilli()
      } catch (_: Exception) {
        try {
          java.time.OffsetDateTime.parse(timestampStr).toInstant().toEpochMilli()
        } catch (_: Exception) {
          java.time.LocalDateTime.parse(timestampStr.substringBefore("+").substringBefore("Z"))
            .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        }
      }
    } else {
      val d = timestampStr.toDoubleOrNull()
      if (d != null) {
        if (d < 100000000000L) (d * 1000).toLong() else d.toLong()
      } else {
        return timestampStr
      }
    }
    val now = System.currentTimeMillis()
    val diffSeconds = (now - time) / 1000
    return when {
      diffSeconds < 0 -> AppStrings.get(R.string.ui_68af8fa298)
      diffSeconds < 5 -> AppStrings.get(R.string.ui_68af8fa298)
      diffSeconds < 60 -> AppStrings.plural(R.plurals.seconds_ago, diffSeconds)
      diffSeconds < 3600 -> AppStrings.plural(R.plurals.minutes_ago, diffSeconds / 60)
      diffSeconds < 86400 -> AppStrings.plural(R.plurals.hours_ago, diffSeconds / 3600)
      else -> AppStrings.plural(R.plurals.days_ago, diffSeconds / 86400)
    }
  } catch (_: Exception) {
    return timestampStr
  }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LogsScreen(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
  val logs by viewModel.logs.collectAsStateWithLifecycle()
  val logDevices by viewModel.logDevices.collectAsStateWithLifecycle()
  val selectedDeviceFilter by viewModel.logsDeviceFilter.collectAsStateWithLifecycle()
  val selectedStatusFilter by viewModel.logsStatusFilter.collectAsStateWithLifecycle()
  val selectedSearchFilter by viewModel.logsSearchFilter.collectAsStateWithLifecycle()
  val paginationState by viewModel.logsPaginationState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(activeProfile?.id) {
    viewModel.refreshLogs(showToast = false)
  }

  DisposableEffect(lifecycleOwner, activeProfile?.id) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> viewModel.startLogsStream()
        Lifecycle.Event.ON_PAUSE -> viewModel.stopLogsStream()
        else -> Unit
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      viewModel.startLogsStream()
    }

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.stopLogsStream()
    }
  }

  val isLiveStreaming by viewModel.isLiveStreaming.collectAsStateWithLifecycle()

  var searchQuery by remember(selectedSearchFilter) { mutableStateOf(selectedSearchFilter.orEmpty()) }
  var showDeviceMenu by remember { mutableStateOf(false) }
  var expandedLogId by remember { mutableStateOf<String?>(null) }
  var showLiveStreamInfo by remember { mutableStateOf(false) }

  LaunchedEffect(searchQuery) {
    delay(350)
    viewModel.setLogsSearchFilter(searchQuery)
  }

  val detectedDevices = remember(logDevices) {
    val devices = logDevices.filter { it.id.isNotBlank() && it.id != NEXTDNS_UNIDENTIFIED_DEVICE_ID }.toMutableList()
    devices += DeviceMetric(
      id = NEXTDNS_UNIDENTIFIED_DEVICE_ID,
      name = AppStrings.get(R.string.logs_unidentified_device)
    )
    devices.distinctBy { it.id }.sortedBy { it.name.lowercase() }
  }

  val filteredLogs = remember(logs, searchQuery, selectedSearchFilter, selectedDeviceFilter, selectedStatusFilter) {
    val normalizedSearch = searchQuery.trim().takeIf { it.isNotEmpty() }
    val serverSearchIsCurrent = selectedSearchFilter == normalizedSearch
    logs.filter { log ->
      val matchesSearch = serverSearchIsCurrent || normalizedSearch == null ||
        log.domain.contains(normalizedSearch, ignoreCase = true) ||
        (log.deviceName?.contains(normalizedSearch, ignoreCase = true) == true) ||
        (log.blockReason?.contains(normalizedSearch, ignoreCase = true) == true)

      val matchesDevice = when (selectedDeviceFilter) {
        null -> true
        NEXTDNS_UNIDENTIFIED_DEVICE_ID ->
          log.deviceId.isNullOrBlank() || log.deviceId == NEXTDNS_UNIDENTIFIED_DEVICE_ID
        else -> log.deviceId == selectedDeviceFilter
      }

      val matchesStatus = selectedStatusFilter == null || log.status == selectedStatusFilter

      matchesSearch && matchesDevice && matchesStatus
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp, bottom = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      LogsHeaderControls(
        selectedDeviceFilter = selectedDeviceFilter,
        detectedDevices = detectedDevices,
        showDeviceMenu = showDeviceMenu,
        onToggleDeviceMenu = { showDeviceMenu = it },
        onSelectDevice = viewModel::setLogsDeviceFilter,
        isLiveStreaming = isLiveStreaming,
        onToggleLiveStream = { viewModel.toggleLiveStream() },
        showLiveStreamInfo = showLiveStreamInfo,
        onToggleLiveStreamInfo = { showLiveStreamInfo = !showLiveStreamInfo },
        onRefreshLogs = { viewModel.refreshLogs() }
      )

      AnimatedVisibility(visible = showLiveStreamInfo) {
        LiveStreamInfoCard()
      }

      LogsSearchField(
        searchQuery = searchQuery,
        onQueryChange = { searchQuery = it }
      )

      LogsStatusFilterRow(
        selectedStatusFilter = selectedStatusFilter,
        onSelectStatus = viewModel::setLogsStatusFilter
      )
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedDeviceFilter) {
      if (selectedDeviceFilter != null) listState.scrollToItem(0)
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 40.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (filteredLogs.isEmpty()) {
        item(key = "empty_logs") { EmptyLogsPlaceholder(searchQuery = searchQuery) }
      }
      items(filteredLogs, key = { it.id }) { log ->
        val isExpanded = expandedLogId == log.id
        LogItemRow(
          log = log,
          isExpanded = isExpanded,
          onToggleExpand = {
            expandedLogId = if (isExpanded) null else log.id
          },
          onAddToAllowlist = {
            viewModel.addToAllowlist(log.domain)
          },
          onAddToDenylist = {
            viewModel.addToDenylist(log.domain)
          },
          modifier = Modifier.animateItemPlacement()
        )
      }
      if (paginationState.hasMore || paginationState.isLoading || paginationState.error != null ||
        paginationState.retentionLimitReached) {
        item(key = "logs_pagination") {
          LogsPaginationFooter(
            state = paginationState,
            onLoadMore = {
              if (paginationState.hasMore) viewModel.loadMoreLogs()
              else viewModel.refreshLogs(showToast = false)
            }
          )
        }
      }
    }
  }
}

// =========================================================================
// Modular Screen Sub-Composables
// =========================================================================

@Composable
internal fun LogsHeaderControls(
  selectedDeviceFilter: String?,
  detectedDevices: List<DeviceMetric>,
  showDeviceMenu: Boolean,
  onToggleDeviceMenu: (Boolean) -> Unit,
  onSelectDevice: (String?) -> Unit,
  isLiveStreaming: Boolean,
  onToggleLiveStream: () -> Unit,
  showLiveStreamInfo: Boolean,
  onToggleLiveStreamInfo: () -> Unit,
  onRefreshLogs: () -> Unit,
  modifier: Modifier = Modifier
) {
  val selectedDeviceName = when (selectedDeviceFilter) {
    null -> AppStrings.get(R.string.all_devices)
    NEXTDNS_UNIDENTIFIED_DEVICE_ID -> AppStrings.get(R.string.logs_unidentified_device)
    else -> detectedDevices.firstOrNull { it.id == selectedDeviceFilter }?.let {
      DeviceLabelResolver.resolve(it.name, it.model, it.id, AppStrings.get(R.string.device_unidentified))
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
          .heightIn(min = 48.dp)
          .bounceClick(scaleDown = 0.99f) { onToggleDeviceMenu(true) },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = selectedDeviceName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )
          Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      DropdownMenu(
        expanded = showDeviceMenu,
        onDismissRequest = { onToggleDeviceMenu(false) },
        modifier = Modifier
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
      ) {
        DropdownMenuItem(
          modifier = Modifier.testTag("logs_device_option_all"),
          text = {
            Text(
              text = AppStrings.get(R.string.all_devices),
              color = if (selectedDeviceFilter == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              fontSize = 12.sp
            )
          },
          onClick = {
            onSelectDevice(null)
            onToggleDeviceMenu(false)
          }
        )
        detectedDevices.forEach { dev ->
          DropdownMenuItem(
            modifier = Modifier.testTag("logs_device_option_${dev.id}"),
            text = {
              Text(
                text = DeviceLabelResolver.resolve(
                  dev.name,
                  dev.model,
                  dev.id,
                  AppStrings.get(R.string.device_unidentified)
                ),
                color = if (dev.id == selectedDeviceFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
              )
            },
            onClick = {
              onSelectDevice(dev.id)
              onToggleDeviceMenu(false)
            }
          )
        }
      }
    }

    Surface(
      modifier = Modifier
        .clip(RoundedCornerShape(10.dp))
        .border(
          1.dp,
          if (isLiveStreaming) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
          RoundedCornerShape(10.dp)
        )
        .heightIn(min = 48.dp)
        .bounceClick(scaleDown = 0.98f, onClick = onToggleLiveStream),
      color = if (isLiveStreaming) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(10.dp)
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (isLiveStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        )
        Text(
          text = if (isLiveStreaming) AppStrings.get(R.string.ui_d6b0f8fc37) else AppStrings.get(R.string.ui_db747e4342),
          color = if (isLiveStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    IconButton(
      onClick = onToggleLiveStreamInfo,
      modifier = Modifier.size(48.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Info,
        contentDescription = AppStrings.get(R.string.ui_97489bb373),
        tint = if (showLiveStreamInfo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(18.dp)
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    IconButton(
      onClick = onRefreshLogs,
      modifier = Modifier.size(48.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = AppStrings.get(R.string.ui_63cd725787),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

private data class LogsStatusOption(
  val status: String?,
  val labelRes: Int,
  val tag: String
)

@Composable
internal fun LogsStatusFilterRow(
  selectedStatusFilter: String?,
  onSelectStatus: (String?) -> Unit,
  modifier: Modifier = Modifier
) {
  val options = listOf(
    LogsStatusOption(null, R.string.logs_filter_all_statuses, "all"),
    LogsStatusOption("blocked", R.string.state_blocked, "blocked"),
    LogsStatusOption("allowed", R.string.state_allowed, "allowed"),
    LogsStatusOption("default", R.string.logs_status_default, "default"),
    LogsStatusOption("error", R.string.logs_status_error, "error")
  )
  LazyRow(
    modifier = modifier.fillMaxWidth().testTag("logs_status_filters"),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(options, key = { it.tag }) { option ->
      FilterChip(
        selected = selectedStatusFilter == option.status,
        onClick = { onSelectStatus(option.status) },
        label = { Text(AppStrings.get(option.labelRes), maxLines = 1) },
        modifier = Modifier.testTag("logs_status_${option.tag}")
      )
    }
  }
}

private fun logStatusLabelResource(status: String): Int = when (status) {
  "blocked" -> R.string.state_blocked
  "allowed" -> R.string.state_allowed
  "default" -> R.string.logs_status_default
  "error" -> R.string.logs_status_error
  else -> R.string.logs_status_unknown
}

@Composable
internal fun LogsPaginationFooter(
  state: LogsPaginationState,
  onLoadMore: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    when {
      state.isLoading -> {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(AppStrings.get(R.string.logs_page_loading), style = MaterialTheme.typography.labelMedium)
      }
      state.error != null -> {
        Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onLoadMore, modifier = Modifier.testTag("logs_page_retry")) {
          Text(AppStrings.get(R.string.logs_load_older))
        }
      }
      state.hasMore -> {
        OutlinedButton(onClick = onLoadMore, modifier = Modifier.testTag("logs_load_older")) {
          Text(AppStrings.get(R.string.logs_load_older))
        }
      }
    }
    if (state.retentionLimitReached) {
      Text(
        AppStrings.get(R.string.logs_history_limit_reached),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun LiveStreamInfoCard(modifier: Modifier = Modifier) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(AppStrings.get(R.string.ui_0ad2f52cde), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
      }
      Text(
        text = AppStrings.get(R.string.live_logs_description),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 11.sp,
        lineHeight = 15.sp
      )
    }
  }
}

@Composable
private fun LogsSearchField(
  searchQuery: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  OutlinedTextField(
    value = searchQuery,
    onValueChange = onQueryChange,
    placeholder = { Text(AppStrings.get(R.string.ui_bb89ec5b3b), color = MaterialTheme.colorScheme.outline, fontSize = 12.5.sp) },
    leadingIcon = {
      Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    },
    trailingIcon = {
      if (searchQuery.isNotBlank()) {
        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.Close, contentDescription = AppStrings.get(R.string.ui_e306951c12), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
      }
    },
    singleLine = true,
    modifier = modifier.fillMaxWidth().testTag("logs_search_field"),
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = MaterialTheme.colorScheme.primary,
      unfocusedBorderColor = MaterialTheme.colorScheme.outline,
      focusedTextColor = MaterialTheme.colorScheme.onSurface,
      unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    shape = RoundedCornerShape(12.dp)
  )
}

@Composable
private fun EmptyLogsPlaceholder(searchQuery: String, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(32.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
      Text(
        text = if (searchQuery.isNotBlank()) AppStrings.get(R.string.ui_935450c7e2) else AppStrings.get(R.string.ui_b0d5218690),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
      )
    }
  }
}

@Composable
internal fun LogItemRow(
  log: DnsLogEntry,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onAddToAllowlist: () -> Unit,
  onAddToDenylist: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isBlocked = log.blocked
  val statusColor = when (log.status) {
    "blocked", "error" -> MaterialTheme.colorScheme.error
    "allowed" -> MaterialTheme.colorScheme.tertiary
    "default" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  var isCopied by remember { mutableStateOf(false) }

  LaunchedEffect(isCopied) {
    if (isCopied) {
      delay(1800)
      isCopied = false
    }
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .border(
        1.dp,
        if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
        RoundedCornerShape(12.dp)
      )
      .bounceClick(onClick = onToggleExpand),
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(statusColor)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).testTag("logs_local_domain_icon")
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = log.domain,
            color = if (log.status == "blocked" || log.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1f)
          )
          Spacer(modifier = Modifier.width(8.dp))

          // Code-style copy box
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(
                if (isCopied) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surface
              )
              .border(
                1.dp,
                if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                RoundedCornerShape(6.dp)
              )
              .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
              .bounceClick(scaleDown = 0.99f) {
                copyToClipboard(context, log.domain, AppStrings.get(R.string.ui_9f714c45ab))
                isCopied = true
              },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
              contentDescription = AppStrings.get(R.string.ui_38f9d7ffb8),
              tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = DeviceLabelResolver.resolve(
            log.deviceName,
            log.deviceModel,
            log.deviceId,
            AppStrings.get(R.string.device_unidentified)
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium
        )

        Text(
          text = formatRelativeTime(log.timestamp),
          color = MaterialTheme.colorScheme.outline,
          fontSize = 10.sp
        )
      }

      if (isBlocked && !log.blockReason.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = log.blockReason,
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
          fontSize = 10.5.sp,
          fontWeight = FontWeight.Medium
        )
      }

      AnimatedVisibility(visible = isExpanded) {
        LogExpandedDetails(
          log = log,
          onAddToAllowlist = onAddToAllowlist,
          onAddToDenylist = onAddToDenylist
        )
      }
    }
  }
}

@Composable
private fun LogExpandedDetails(
  log: DnsLogEntry,
  onAddToAllowlist: () -> Unit,
  onAddToDenylist: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline,
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
      )
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(AppStrings.get(R.string.protocol_label, log.protocol.ifBlank { "DNS-over-HTTPS (DoH)" }), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
        Text(AppStrings.get(R.string.client_ip, log.clientIp ?: AppStrings.get(R.string.unavailable)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(AppStrings.get(R.string.response_time, log.responseTimeMs?.let { "$it ms" } ?: AppStrings.get(R.string.unavailable)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
        Text(
          text = AppStrings.get(R.string.status_label, AppStrings.get(logStatusLabelResource(log.status))),
          color = when (log.status) {
            "blocked", "error" -> MaterialTheme.colorScheme.error
            "allowed" -> MaterialTheme.colorScheme.tertiary
            "default" -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      NextDnsOutlineButton(
        text = AppStrings.get(R.string.ui_9e1b588ac9),
        onClick = onAddToAllowlist,
        colors = NextDnsOutlineButtonColors(
          borderColor = MaterialTheme.colorScheme.tertiary,
          contentColor = MaterialTheme.colorScheme.tertiary
        ),
        icon = Icons.Default.Check
      )
      NextDnsOutlineButton(
        text = AppStrings.get(R.string.ui_b768ffe3c8),
        onClick = onAddToDenylist,
        colors = NextDnsOutlineButtonColors(
          borderColor = MaterialTheme.colorScheme.error,
          contentColor = MaterialTheme.colorScheme.error
        ),
        icon = Icons.Default.Block
      )
    }
  }
}



