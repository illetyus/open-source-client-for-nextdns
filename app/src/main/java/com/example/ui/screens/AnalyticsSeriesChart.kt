package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AnalyticsSeriesState
import com.example.data.model.SeriesMetric
import com.example.data.model.SeriesPoint
import com.example.data.model.SeriesRow
import com.example.i18n.AppStrings
import com.example.ui.components.NextDnsCard
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat

private fun analyticsSeriesRowLabel(row: SeriesRow, metric: SeriesMetric): String {
  if (metric != SeriesMetric.STATUS) return row.label
  return when ((row.key.ifBlank { row.label }).trim().lowercase(java.util.Locale.ROOT)) {
    "allowed" -> AppStrings.get(R.string.state_allowed)
    "blocked" -> AppStrings.get(R.string.state_blocked)
    "default" -> AppStrings.get(R.string.logs_status_default)
    "error" -> AppStrings.get(R.string.logs_status_error)
    else -> row.label
  }
}

@Composable
fun AnalyticsSeriesChart(
  state: AnalyticsSeriesState,
  selectedMetric: SeriesMetric,
  domainStatus: String?,
  rootGrouped: Boolean,
  onMetricSelected: (SeriesMetric) -> Unit,
  onDomainStatusSelected: (String?) -> Unit,
  onRootGroupedChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  var menuExpanded by remember { mutableStateOf(false) }
  var selectedRowKey by remember(state.rows) { mutableStateOf(state.rows.firstOrNull()?.key) }
  var selectedPointIndex by remember(state.rows) { mutableStateOf(0) }
  val selectedRow = state.rows.firstOrNull { it.key == selectedRowKey } ?: state.rows.firstOrNull()
  val selectedRowLabel = selectedRow?.let { analyticsSeriesRowLabel(it, selectedMetric) }
  val selectedPoint = selectedRow?.points?.getOrNull(selectedPointIndex)
  val numberFormat = remember(AppStrings.locale) { NumberFormat.getIntegerInstance(AppStrings.locale) }
  val timestampFormatter = remember(AppStrings.locale) {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
      .withLocale(AppStrings.locale)
      .withZone(ZoneOffset.UTC)
  }

  NextDnsCard(
    modifier = modifier,
    title = AppStrings.get(R.string.analytics_series_title),
    subtitle = AppStrings.get(R.string.analytics_series_time_zone)
  ) {
    Box {
      OutlinedButton(
        onClick = { menuExpanded = true },
        modifier = Modifier.fillMaxWidth().testTag("analytics_series_metric")
      ) {
        Text(AppStrings.get(seriesMetricLabel(selectedMetric)), maxLines = 1)
      }
      DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        SeriesMetric.entries.forEach { metric ->
          DropdownMenuItem(
            modifier = Modifier.testTag("analytics_series_metric_${metric.name.lowercase()}"),
            text = { Text(AppStrings.get(seriesMetricLabel(metric))) },
            onClick = {
              menuExpanded = false
              onMetricSelected(metric)
            }
          )
        }
      }
    }

    if (selectedMetric == SeriesMetric.DOMAINS) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(listOf(null, "default", "blocked", "allowed")) { index, status ->
          val label = when (status) {
            null -> R.string.analytics_series_domain_all
            "default" -> R.string.analytics_series_domain_default
            "blocked" -> R.string.analytics_series_domain_blocked
            else -> R.string.analytics_series_domain_allowed
          }
          FilterChip(
            selected = domainStatus == status,
            onClick = { onDomainStatusSelected(status) },
            label = { Text(AppStrings.get(label), maxLines = 1) },
            modifier = Modifier.testTag("analytics_series_domain_status_$index")
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(AppStrings.get(R.string.analytics_series_group_root), style = MaterialTheme.typography.bodyMedium)
        Switch(
          checked = rootGrouped,
          onCheckedChange = onRootGroupedChanged,
          modifier = Modifier.testTag("analytics_series_root_toggle")
        )
      }
    }

    when {
      state.loading -> CircularProgressIndicator(
        modifier = Modifier.align(Alignment.CenterHorizontally).size(24.dp).testTag("analytics_series_loading"),
        strokeWidth = 2.dp
      )
      state.errorMessage != null -> Text(
        text = state.errorMessage,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("analytics_series_error")
      )
      selectedRow == null -> Text(
        text = AppStrings.get(R.string.analytics_series_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("analytics_series_empty")
      )
      else -> {
        SeriesRowSelector(
          rows = state.rows,
          selectedKey = selectedRow.key,
          metric = selectedMetric,
          onSelect = { selectedRowKey = it; selectedPointIndex = 0 }
        )
        Text(
          text = AppStrings.get(R.string.analytics_series_selected_entity, selectedRowLabel.orEmpty()),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(top = 8.dp)
        )
        AnalyticsLineChart(
          row = selectedRow,
          rowLabel = selectedRowLabel.orEmpty(),
          selectedPointIndex = selectedPointIndex,
          onPointSelected = { selectedPointIndex = it },
          modifier = Modifier.fillMaxWidth().height(168.dp).padding(top = 8.dp)
        )
        selectedPoint?.let { point ->
          val value = point.queries?.let(numberFormat::format) ?: AppStrings.get(R.string.analytics_series_no_value)
          Text(
            text = AppStrings.get(R.string.analytics_series_point_detail, timestampFormatter.format(point.at), value),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("analytics_series_selected_point")
          )
        }
        Text(
          text = AppStrings.get(R.string.analytics_series_table_heading),
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )
        Column(
          modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          selectedRow.points.takeLast(6).forEach { point ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(timestampFormatter.format(point.at), style = MaterialTheme.typography.bodySmall)
              Text(
                point.queries?.let(numberFormat::format) ?: AppStrings.get(R.string.analytics_series_no_value),
                style = MaterialTheme.typography.bodySmall
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SeriesRowSelector(
  rows: List<SeriesRow>,
  selectedKey: String,
  metric: SeriesMetric,
  onSelect: (String) -> Unit
) {
  LazyRow(
    modifier = Modifier.fillMaxWidth().testTag("analytics_series_rows"),
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    itemsIndexed(rows, key = { index, row -> "$index:${row.key}" }) { index, row ->
      FilterChip(
        selected = row.key == selectedKey,
        onClick = { onSelect(row.key) },
        label = { Text(analyticsSeriesRowLabel(row, metric), maxLines = 1) },
        modifier = Modifier.testTag("analytics_series_row_$index")
      )
    }
  }
}

@Composable
private fun AnalyticsLineChart(
  row: SeriesRow,
  rowLabel: String,
  selectedPointIndex: Int,
  onPointSelected: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val lineColor = MaterialTheme.colorScheme.primary
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val description = AppStrings.get(R.string.analytics_series_chart_accessibility, rowLabel)
  Canvas(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .testTag("analytics_series_line_chart")
      .semantics { contentDescription = description }
      .pointerInput(row.points) {
        detectTapGestures { position ->
          if (row.points.isNotEmpty()) {
            val fraction = (position.x / size.width).coerceIn(0f, 1f)
            onPointSelected((fraction * (row.points.size - 1)).toInt())
          }
        }
      }
  ) {
    val left = 18.dp.toPx()
    val right = size.width - 10.dp.toPx()
    val top = 12.dp.toPx()
    val bottom = size.height - 16.dp.toPx()
    val maxValue = row.points.mapNotNull(SeriesPoint::queries).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    repeat(4) { line ->
      val y = top + (bottom - top) * line / 3f
      drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
    }
    val values = row.points
    fun x(index: Int): Float = if (values.size <= 1) (left + right) / 2f
      else left + (right - left) * index / (values.size - 1).toFloat()
    fun y(value: Long): Float = bottom - (bottom - top) * (value.toDouble() / maxValue.toDouble()).toFloat()
    values.zipWithNext().forEachIndexed { index, (first, second) ->
      if (first.queries != null && second.queries != null) {
        drawLine(
          color = lineColor,
          start = Offset(x(index), y(first.queries)),
          end = Offset(x(index + 1), y(second.queries)),
          strokeWidth = 3.dp.toPx(),
          cap = StrokeCap.Round
        )
      }
    }
    values.forEachIndexed { index, point ->
      point.queries?.let { value ->
        drawCircle(
          color = if (index == selectedPointIndex) lineColor else lineColor.copy(alpha = 0.7f),
          radius = if (index == selectedPointIndex) 5.dp.toPx() else 3.dp.toPx(),
          center = Offset(x(index), y(value))
        )
      }
    }
  }
}

private fun seriesMetricLabel(metric: SeriesMetric): Int = when (metric) {
  SeriesMetric.STATUS -> R.string.analytics_series_metric_status
  SeriesMetric.DEVICES -> R.string.analytics_series_metric_devices
  SeriesMetric.DOMAINS -> R.string.analytics_series_metric_domains
  SeriesMetric.REASONS -> R.string.analytics_series_metric_reasons
  SeriesMetric.COUNTRIES -> R.string.analytics_series_metric_countries
  SeriesMetric.GAFAM -> R.string.analytics_series_metric_gafam
  SeriesMetric.DNSSEC -> R.string.analytics_series_metric_dnssec
  SeriesMetric.ENCRYPTION -> R.string.analytics_series_metric_encryption
  SeriesMetric.PROTOCOLS -> R.string.analytics_series_metric_protocols
  SeriesMetric.IPS -> R.string.analytics_series_metric_ips
  SeriesMetric.QUERY_TYPES -> R.string.analytics_series_metric_query_types
  SeriesMetric.IP_VERSIONS -> R.string.analytics_series_metric_ip_versions
}

