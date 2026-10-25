package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DiagnosticConnectionState
import com.example.data.model.diagnosticConnectionState
import com.example.i18n.AppStrings
import com.example.ui.components.NextDnsButton
import com.example.ui.components.NextDnsCard
import com.example.ui.viewmodel.NextDnsViewModel
import java.text.DateFormat
import java.util.Date

@Composable
internal fun NetworkDiagnosticsCard(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
  val testResult by viewModel.testResult.collectAsStateWithLifecycle()
  val isDiagnosticRunning by viewModel.isDiagnosticRunning.collectAsStateWithLifecycle()
  val diagnosticCheckedAt = remember(testResult.lastTestedTime, AppStrings.locale) {
    testResult.lastTestedTime.takeIf { it > 0L }?.let { DateFormat.getTimeInstance(DateFormat.SHORT, AppStrings.locale).format(Date(it)) }
  }
  val diagnosticState = diagnosticConnectionState(activeProfile, testResult, isDiagnosticRunning)
  val diagnosticLabel = when (diagnosticState) {
    DiagnosticConnectionState.TESTING -> AppStrings.get(R.string.ui_5ed3c5cf66)
    DiagnosticConnectionState.ERROR -> AppStrings.get(R.string.ui_19b9ff3444)
    DiagnosticConnectionState.UNCONFIGURED -> AppStrings.get(R.string.ui_a13d9a7458)
    DiagnosticConnectionState.MATCHED_PROFILE -> AppStrings.get(R.string.ui_8c25d923c8)
    DiagnosticConnectionState.UNVERIFIED_PROFILE -> AppStrings.get(R.string.diagnostic_unverified_title)
    DiagnosticConnectionState.NO_SELECTED_PROFILE -> AppStrings.get(R.string.diagnostic_detected_title)
  }
  val statusColor = when (diagnosticState) {
    DiagnosticConnectionState.MATCHED_PROFILE -> MaterialTheme.colorScheme.tertiary
    DiagnosticConnectionState.TESTING -> MaterialTheme.colorScheme.primary
    DiagnosticConnectionState.ERROR,
    DiagnosticConnectionState.UNCONFIGURED,
    DiagnosticConnectionState.UNVERIFIED_PROFILE -> MaterialTheme.colorScheme.error
    DiagnosticConnectionState.NO_SELECTED_PROFILE -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  val resolverValue = testResult.resolver
  val anycastValue = testResult.anycast

  Column(modifier = modifier.fillMaxWidth().testTag("setup_network_diagnostics")) {
    NextDnsCard(
      title = AppStrings.get(R.string.ui_33cbc60e2e),
      subtitle = AppStrings.get(R.string.ui_ed7561918d)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = diagnosticLabel,
              color = statusColor,
              fontWeight = FontWeight.Bold,
              fontSize = 13.5.sp,
              modifier = Modifier.testTag("setup_diagnostic_status_label")
            )
            diagnosticCheckedAt?.let { checkedAt ->
              Text(
                text = AppStrings.get(R.string.diagnostic_source),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                modifier = Modifier.testTag("diagnostic_source")
              )
              Text(
                text = AppStrings.get(R.string.diagnostic_checked_at, checkedAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                modifier = Modifier.testTag("diagnostic_checked_at")
              )
            }
            if (testResult.serverPoP.isNotBlank()) {
              Text(
                text = AppStrings.get(
                  R.string.server_latency,
                  testResult.serverPoP,
                  testResult.protocol,
                  testResult.latencyMs
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
              )
            }
            Text(
              text = AppStrings.get(R.string.diagnostic_region_unavailable),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.5.sp,
              modifier = Modifier.testTag("diagnostic_region_unavailable")
            )
            if (!resolverValue.isNullOrBlank()) {
              Text(
                text = AppStrings.get(R.string.diagnostic_resolver_identifier, resolverValue),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
              )
            }
            if (anycastValue != null) {
              Text(
                text = AppStrings.get(
                  if (anycastValue) R.string.diagnostic_anycast_enabled else R.string.diagnostic_anycast_disabled
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
              )
            }
            if (testResult.serverPoP.isBlank() && testResult.resolver.isNullOrBlank()) {
              Text(
                text = AppStrings.get(R.string.diagnostic_server_information_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
              )
            }
          }

          NextDnsButton(
            text = if (isDiagnosticRunning) AppStrings.get(R.string.ui_5ed3c5cf66) else AppStrings.get(R.string.ui_e4f71092ee),
            onClick = { viewModel.runDiagnostic(showToast = true) },
            modifier = Modifier.testTag("setup_run_diagnostic"),
            icon = Icons.Default.Refresh,
            enabled = !isDiagnosticRunning
          )
        }
      }
    }
  }
}
