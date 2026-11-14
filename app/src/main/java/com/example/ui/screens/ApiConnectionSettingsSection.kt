package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.repository.ApiConnectionStatus
import com.example.i18n.AppStrings
import com.example.ui.components.NextDnsCard
import com.example.ui.viewmodel.NextDnsViewModel
import kotlinx.coroutines.delay

internal fun connectedProfileStatusText(profileCount: Int): String =
  AppStrings.get(
    if (profileCount == 1) R.string.account_status_connected
    else R.string.account_status_connected_many,
    profileCount
  )

@Composable
fun ApiConnectionSettingsSection(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
  val apiStatus by viewModel.apiStatus.collectAsStateWithLifecycle()
  var showApiKey by remember { mutableStateOf(false) }
  var showLogoutConfirm by remember { mutableStateOf(false) }

  LaunchedEffect(showApiKey) {
    if (showApiKey) {
      delay(10_000L)
      showApiKey = false
    }
  }

  DisposableEffect(showApiKey, activity) {
    if (showApiKey) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    onDispose {
      if (showApiKey) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }

  NextDnsCard(
    modifier = modifier.testTag("settings_api_connection"),
    title = AppStrings.get(R.string.ui_f9b1cde612),
    subtitle = AppStrings.get(R.string.ui_4400dad370)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = when (val status = apiStatus) {
          is ApiConnectionStatus.Connected -> connectedProfileStatusText(status.profileCount)
          ApiConnectionStatus.Connecting -> AppStrings.get(R.string.account_status_connecting)
          ApiConnectionStatus.Disconnected -> AppStrings.get(R.string.account_status_disconnected)
          is ApiConnectionStatus.Error -> AppStrings.get(R.string.account_status_error)
        },
        color = when (apiStatus) {
          is ApiConnectionStatus.Connected -> MaterialTheme.colorScheme.tertiary
          is ApiConnectionStatus.Error -> MaterialTheme.colorScheme.error
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontSize = 12.sp,
        modifier = Modifier.testTag("settings_api_connection_status")
      )

      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = when {
              !hasApiKey -> AppStrings.get(R.string.ui_ebf30ef1ee)
              showApiKey -> viewModel.currentApiKeyForSensitiveUse()
              else -> viewModel.maskedApiKey()
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("settings_api_key_value")
          )

          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { showApiKey = !showApiKey }, modifier = Modifier.size(48.dp)) {
              Icon(
                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = AppStrings.get(R.string.toggle_visibility),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
            }
            if (hasApiKey) {
              IconButton(
                onClick = {
                  val apiKey = viewModel.currentApiKeyForSensitiveUse()
                  copySensitiveApiKey(context, apiKey)
                  scheduleApiKeyClipboardClear(context, apiKey)
                },
                modifier = Modifier.size(48.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = AppStrings.get(R.string.ui_a8bcca42d9),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }

      OutlinedButton(
        onClick = { showLogoutConfirm = true },
        modifier = Modifier.fillMaxWidth().testTag("settings_logout_button"),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
      ) {
        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(AppStrings.get(R.string.ui_283092d12a), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
      }
    }
  }

  if (showLogoutConfirm) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirm = false },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp),
      title = {
        Text(AppStrings.get(R.string.ui_984bb24efa), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
      },
      text = {
        Text(AppStrings.get(R.string.ui_4448d81aad), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
      },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirm = false
            viewModel.logout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text(AppStrings.get(R.string.ui_283092d12a), color = MaterialTheme.colorScheme.onError)
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogoutConfirm = false }) {
          Text(AppStrings.get(R.string.ui_7227874813), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    )
  }
}

private fun copySensitiveApiKey(context: Context, apiKey: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  val clip = ClipData.newPlainText(AppStrings.get(R.string.ui_92e563511e), apiKey)
  clip.description.extras = PersistableBundle().apply {
    putBoolean("android.content.extra.IS_SENSITIVE", true)
  }
  clipboard.setPrimaryClip(clip)
  if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
    Toast.makeText(context, AppStrings.get(R.string.ui_41809d48c3), Toast.LENGTH_SHORT).show()
  }
}

private fun scheduleApiKeyClipboardClear(context: Context, expectedApiKey: String) {
  val appContext = context.applicationContext
  Handler(Looper.getMainLooper()).postDelayed(
    { clearApiKeyClipboardIfUnchanged(appContext, expectedApiKey) },
    30_000L
  )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

private fun clearApiKeyClipboardIfUnchanged(context: Context, expectedApiKey: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  val current = clipboard.primaryClip
    ?.takeIf { it.itemCount > 0 }
    ?.getItemAt(0)
    ?.coerceToText(context)
    ?.toString()
  if (current != expectedApiKey) return
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
  else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
}
