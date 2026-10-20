package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.diagnostics.LibreSpeedClient
import com.example.data.diagnostics.SavedSpeedTestServer
import com.example.data.diagnostics.SpeedTestConfigError
import com.example.data.diagnostics.SpeedTestSettingsStore
import com.example.data.diagnostics.SpeedTestTransferResult
import com.example.data.diagnostics.validateLibreSpeedServer
import com.example.i18n.AppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun SpeedTestDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val store = remember(context) { SpeedTestSettingsStore(context) }
  val client = remember { LibreSpeedClient() }
  val saved = remember(context) { store.read() }

  var serverUrl by remember(saved?.baseUrl) { mutableStateOf(saved?.baseUrl.orEmpty()) }
  var privacyUrl by remember(saved?.privacyPolicyUrl) { mutableStateOf(saved?.privacyPolicyUrl.orEmpty()) }
  var backendPath by remember(saved?.backendPath) { mutableStateOf(saved?.backendPath ?: "backend") }
  var authorizationConfirmed by remember { mutableStateOf(false) }
  var meteredConfirmed by remember { mutableStateOf(false) }
  var running by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf(0f) }
  var pingText by remember { mutableStateOf<String?>(null) }
  var downloadResult by remember { mutableStateOf<SpeedTestTransferResult?>(null) }
  var uploadResult by remember { mutableStateOf<SpeedTestTransferResult?>(null) }
  var error by remember { mutableStateOf<SpeedTestConfigError?>(null) }
  var testFailed by remember { mutableStateOf(false) }
  var uploadConfirmation by remember { mutableStateOf(false) }
  var activeJob by remember { mutableStateOf<Job?>(null) }

  val validation = remember(serverUrl, privacyUrl, backendPath, authorizationConfirmed) {
    validateLibreSpeedServer(
      baseUrl = serverUrl,
      privacyPolicyUrl = privacyUrl,
      backendPath = backendPath,
      authorizationConfirmed = authorizationConfirmed
    )
  }

  val currentJob by rememberUpdatedState(activeJob)
  DisposableEffect(lifecycleOwner) {
    val observer = speedTestLifecycleObserver { currentJob }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      currentJob?.cancel()
    }
  }

  val invalidateMeasurements = {
    pingText = null
    downloadResult = null
    uploadResult = null
    testFailed = false
    uploadConfirmation = false
  }
  val invalidateServerAuthorization = {
    authorizationConfirmed = false
    invalidateMeasurements()
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).padding(horizontal = 20.dp).heightIn(max = 760.dp),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          AppStrings.get(R.string.speed_test_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold
        )
        Text(AppStrings.get(R.string.speed_test_disclosure), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
          value = serverUrl,
          onValueChange = { serverUrl = it; error = null; invalidateServerAuthorization() },
          modifier = Modifier.fillMaxWidth().testTag("speed_test_server"),
          label = { Text(AppStrings.get(R.string.speed_test_server_url)) },
          placeholder = { Text("https://speed.example") },
          singleLine = true,
          enabled = !running
        )
        OutlinedTextField(
          value = backendPath,
          onValueChange = { backendPath = it; error = null; invalidateServerAuthorization() },
          modifier = Modifier.fillMaxWidth(),
          label = { Text(AppStrings.get(R.string.speed_test_backend_path)) },
          singleLine = true,
          enabled = !running
        )
        OutlinedTextField(
          value = privacyUrl,
          onValueChange = { privacyUrl = it; error = null; invalidateServerAuthorization() },
          modifier = Modifier.fillMaxWidth(),
          label = { Text(AppStrings.get(R.string.speed_test_privacy_url)) },
          placeholder = { Text("https://speed.example/privacy") },
          singleLine = true,
          enabled = !running
        )
        TextButton(
          onClick = { validation.server?.let { context.openExternalUrl(it.privacyPolicyUrl.toString()) } },
          enabled = validation.server != null && !running,
          modifier = Modifier.align(Alignment.Start)
        ) {
          Text(AppStrings.get(R.string.speed_test_open_privacy))
        }

        ConsentRow(
          checked = authorizationConfirmed,
          onCheckedChange = { authorizationConfirmed = it; error = null; invalidateMeasurements() },
          enabled = !running,
          text = AppStrings.get(R.string.speed_test_authorization),
          modifier = Modifier.testTag("speed_test_authorization")
        )
        ConsentRow(
          checked = meteredConfirmed,
          onCheckedChange = { meteredConfirmed = it; invalidateMeasurements() },
          enabled = !running,
          text = AppStrings.get(R.string.speed_test_transfer_consent),
          modifier = Modifier.testTag("speed_test_transfer_consent")
        )

        Text(
          AppStrings.get(R.string.speed_test_data_usage),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val showValidationError = serverUrl.isNotBlank() || privacyUrl.isNotBlank() || backendPath != "backend"
        val visibleError = error ?: validation.error
          ?.takeIf { showValidationError }
          ?.takeUnless { it == SpeedTestConfigError.AUTHORIZATION_REQUIRED }
        visibleError?.let { Text(AppStrings.get(it.toMessageResource()), color = MaterialTheme.colorScheme.error) }
        if (testFailed) {
          Text(AppStrings.get(R.string.speed_test_error), color = MaterialTheme.colorScheme.error)
        }
        if (running) {
          LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
          Text(AppStrings.get(R.string.speed_test_running), style = MaterialTheme.typography.bodySmall)
        }
        pingText?.let {
          Text(AppStrings.get(R.string.speed_test_http_latency, it), modifier = Modifier.testTag("speed_test_latency"))
        }
        downloadResult?.let {
          Text(AppStrings.get(R.string.speed_test_download_result, formatMbps(it.megabitsPerSecond)))
        }
        uploadResult?.let {
          Text(AppStrings.get(R.string.speed_test_upload_result, formatMbps(it.megabitsPerSecond)))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = {
              store.clear()
              serverUrl = ""
              privacyUrl = ""
              backendPath = "backend"
              invalidateServerAuthorization()
              error = null
            },
            enabled = !running && (serverUrl.isNotBlank() || privacyUrl.isNotBlank()),
            modifier = Modifier.weight(1f)
          ) {
            Text(AppStrings.get(R.string.speed_test_clear_server))
          }
          OutlinedButton(
            onClick = {
              validation.server?.let { configured ->
                store.save(
                  SavedSpeedTestServer(
                    configured.baseUrl.toString(),
                    configured.privacyPolicyUrl.toString(),
                    configured.backendPath
                  )
                )
                error = null
              } ?: run { error = validation.error ?: SpeedTestConfigError.INVALID_SERVER_URL }
            },
            enabled = !running && validation.server != null,
            modifier = Modifier.weight(1f)
          ) {
            Text(AppStrings.get(R.string.speed_test_save_server))
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = {
              val configured = validation.server ?: return@Button
              if (!meteredConfirmed) return@Button
              store.save(
                SavedSpeedTestServer(
                  configured.baseUrl.toString(),
                  configured.privacyPolicyUrl.toString(),
                  configured.backendPath
                )
              )
              error = null
              testFailed = false
              pingText = null
              downloadResult = null
              uploadResult = null
              progress = 0f
              running = true
              activeJob = scope.launch {
                try {
                  val ping = client.measurePing(configured)
                  pingText = formatMs(ping.latencyMs)
                  val download = client.measureDownload(configured) { transferred, total ->
                    val fraction = transferred.toFloat() / total.coerceAtLeast(1L)
                    if (transferred == total || transferred % 262_144L < 16_384L) {
                      scope.launch { progress = fraction }
                    }
                  }
                  downloadResult = download
                } catch (cancelled: CancellationException) {
                  throw cancelled
                } catch (_: Exception) {
                  testFailed = true
                } finally {
                  running = false
                  activeJob = null
                }
              }
            },
            enabled = !running && validation.server != null && authorizationConfirmed && meteredConfirmed,
            modifier = Modifier.weight(1f).testTag("speed_test_start")
          ) {
            Text(AppStrings.get(R.string.speed_test_start))
          }
          if (running) {
            OutlinedButton(
              onClick = { activeJob?.cancel() },
              modifier = Modifier.weight(1f)
            ) { Text(AppStrings.get(R.string.speed_test_cancel)) }
          } else {
            OutlinedButton(
              onClick = { uploadConfirmation = true },
              enabled = downloadResult != null && validation.server != null && authorizationConfirmed && meteredConfirmed,
              modifier = Modifier.weight(1f).testTag("speed_test_upload")
            ) { Text(AppStrings.get(R.string.speed_test_upload_action)) }
          }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
          Text(AppStrings.get(R.string.speed_test_close))
        }
      }
    }
  }

  if (uploadConfirmation) {
    AlertDialog(
      onDismissRequest = { uploadConfirmation = false },
      title = { Text(AppStrings.get(R.string.speed_test_upload_confirm_title)) },
      text = { Text(AppStrings.get(R.string.speed_test_upload_confirm_body)) },
      confirmButton = {
        TextButton(onClick = {
          val configured = validation.server ?: return@TextButton
          uploadConfirmation = false
          testFailed = false
          progress = 0f
          running = true
          activeJob = scope.launch {
            try {
              uploadResult = client.measureUpload(configured) { transferred, total ->
                val fraction = transferred.toFloat() / total.coerceAtLeast(1L)
                if (transferred == total || transferred % 262_144L < 32_768L) {
                  scope.launch { progress = fraction }
                }
              }
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              testFailed = true
            } finally {
              running = false
              activeJob = null
            }
          }
        }) { Text(AppStrings.get(R.string.speed_test_upload_confirm_button)) }
      },
      dismissButton = {
        TextButton(onClick = { uploadConfirmation = false }) {
          Text(AppStrings.get(R.string.speed_test_cancel))
        }
      }
    )
  }
}

@Composable
private fun ConsentRow(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  enabled: Boolean,
  text: String,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
  }
}

internal fun speedTestLifecycleObserver(activeJob: () -> Job?): LifecycleEventObserver =
  LifecycleEventObserver { _, event ->
    if (event == Lifecycle.Event.ON_STOP) activeJob()?.cancel()
  }

private fun Context.openExternalUrl(url: String) {
  runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun SpeedTestConfigError.toMessageResource(): Int = when (this) {
  SpeedTestConfigError.INVALID_SERVER_URL,
  SpeedTestConfigError.SERVER_URL_QUERY_NOT_ALLOWED -> R.string.speed_test_invalid_server
  SpeedTestConfigError.HTTPS_REQUIRED -> R.string.speed_test_https_required
  SpeedTestConfigError.CREDENTIALS_NOT_ALLOWED -> R.string.speed_test_credentials_not_allowed
  SpeedTestConfigError.INVALID_PRIVACY_URL -> R.string.speed_test_invalid_privacy_url
  SpeedTestConfigError.INVALID_BACKEND_PATH -> R.string.speed_test_invalid_backend
  SpeedTestConfigError.AUTHORIZATION_REQUIRED -> R.string.speed_test_authorization_required
}

private fun formatMbps(value: Double): String = String.format(AppStrings.locale, "%.1f", value)
private fun formatMs(value: Double): String = String.format(AppStrings.locale, "%.1f", value)
