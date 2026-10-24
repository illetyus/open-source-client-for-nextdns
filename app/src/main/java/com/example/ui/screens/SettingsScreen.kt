package com.example.ui.screens

import com.example.i18n.UiLabels

import com.example.R
import com.example.i18n.AppStrings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConfigSettings
import com.example.data.notifications.NotificationPreferences
import com.example.data.notifications.NotificationSettings
import com.example.data.notifications.NotificationWorkScheduler
import com.example.data.preferences.ThemePreferences
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NextDnsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val screenScope = rememberCoroutineScope()
  val notificationPreferences = remember(context) {
    NotificationPreferences(context.applicationContext)
  }
  val notificationSettings by notificationPreferences.settings.collectAsStateWithLifecycle(
    initialValue = NotificationSettings()
  )
  var pendingNotificationEnable by remember {
    mutableStateOf<NotificationToggleType?>(null)
  }
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    if (uri != null) {
      screenScope.launch {
        viewModel.exportLogsToDocument { context.contentResolver.openOutputStream(uri) }
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    val pending = pendingNotificationEnable
    pendingNotificationEnable = null

    if (granted && pending != null) {
      screenScope.launch {
        when (pending) {
          NotificationToggleType.CONFIG_CHANGES ->
            notificationPreferences.setConfigChangeAlertsEnabled(true)
          NotificationToggleType.DAILY_SUMMARY ->
            notificationPreferences.setDailySummaryEnabled(true)
        }
        NotificationWorkScheduler.reconcile(
          context = context,
          runImmediately = true,
          baselineConfig = pending == NotificationToggleType.CONFIG_CHANGES
        )
      }
    } else if (!granted) {
      viewModel.showMessage(
        AppStrings.get(R.string.ui_aa12c2d136),
        isError = true
      )
    }
  }

  fun updateNotificationToggle(
    type: NotificationToggleType,
    enabled: Boolean
  ) {
    if (!enabled) {
      screenScope.launch {
        when (type) {
          NotificationToggleType.CONFIG_CHANGES ->
            notificationPreferences.setConfigChangeAlertsEnabled(false)
          NotificationToggleType.DAILY_SUMMARY ->
            notificationPreferences.setDailySummaryEnabled(false)
        }
        NotificationWorkScheduler.reconcile(context)
      }
      return
    }

    val permissionGranted =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    if (permissionGranted) {
      screenScope.launch {
        when (type) {
          NotificationToggleType.CONFIG_CHANGES ->
            notificationPreferences.setConfigChangeAlertsEnabled(true)
          NotificationToggleType.DAILY_SUMMARY ->
            notificationPreferences.setDailySummaryEnabled(true)
        }
        NotificationWorkScheduler.reconcile(
          context = context,
          runImmediately = true,
          baselineConfig = type == NotificationToggleType.CONFIG_CHANGES
        )
      }
    } else {
      pendingNotificationEnable = type
      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
  val configSettings by viewModel.configSettings.collectAsStateWithLifecycle()

  var showConnectionSettings by rememberSaveable { mutableStateOf(false) }
  var showDeleteProfileDialog by remember { mutableStateOf(false) }
  val profileName = activeProfile?.name ?: AppStrings.get(R.string.ui_0a4d547a3f)
  BackHandler(enabled = showConnectionSettings) {
    showConnectionSettings = false
  }

  if (showConnectionSettings) {
    Column(
      modifier = modifier
        .fillMaxSize()
        .testTag("settings_connection_page")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { showConnectionSettings = false },
          modifier = Modifier.testTag("settings_connection_back")
        ) {
          Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = AppStrings.get(R.string.ui_80ad54ad05),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = AppStrings.get(R.string.nav_connection),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = AppStrings.get(R.string.settings_connection_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      AccountScreen(
        viewModel = viewModel,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      )
    }
    return
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .testTag("settings_scroll_list"),
    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      SettingsSectionHeading(
        title = AppStrings.get(R.string.nextdns_service_settings),
        tag = "settings_section_service"
      )
    }

    item {
      SettingsConnectionEntry(onClick = { showConnectionSettings = true })
    }

    item {
      LogsAndPrivacySection(
        configSettings = configSettings,
        viewModel = viewModel,
        onExportClick = { exportLauncher.launch("nextdns_logs.csv") },
        onClearLogsClick = {
          viewModel.clearLogs()
        }
      )
    }

    item {
      BlockPageSection(
        blockPageEnabled = configSettings.blockPage,
        onToggleBlockPage = { viewModel.toggleBlockPage(it) }
      )
    }

    item {
      PerformanceSection(
        configSettings = configSettings,
        viewModel = viewModel
      )
    }

    item {
      Web3Section(
        web3Enabled = configSettings.web3,
        onToggleWeb3 = { viewModel.toggleWeb3(it) }
      )
    }

    item {
      Column(
        modifier = Modifier.fillMaxWidth().testTag("settings_section_app_preferences"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        SettingsSectionHeading(title = AppStrings.get(R.string.app_preferences))
        AppearanceSection()
        com.example.ui.components.LanguagePicker(Modifier.fillMaxWidth())
        NotificationSettingsSection(
          settings = notificationSettings,
          onConfigChangeAlertsChanged = {
            updateNotificationToggle(NotificationToggleType.CONFIG_CHANGES, it)
          },
          onDailySummaryChanged = {
            updateNotificationToggle(NotificationToggleType.DAILY_SUMMARY, it)
          }
        )
        Column(
          modifier = Modifier.fillMaxWidth().testTag("settings_section_legal"),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          NextDnsCard(title = AppStrings.get(R.string.legal_and_about)) {
            LegalDocumentLinks()
          }
        }
      }
    }

    item {
      Column(
        modifier = Modifier.fillMaxWidth().testTag("settings_section_delete"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        SettingsSectionHeading(title = AppStrings.get(R.string.settings_delete_profile))
        ProfileActionsSection(
          profileName = profileName,
          onDeleteProfile = { showDeleteProfileDialog = true }
        )
      }
    }
  }

  if (showDeleteProfileDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteProfileDialog = false },
      title = { Text(AppStrings.get(R.string.ui_26c4159180)) },
      text = {
        Text(
          AppStrings.get(R.string.delete_profile_named, profileName)
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteProfileDialog = false
            activeProfile?.id?.let(viewModel::deleteProfile)
          }
        ) {
          Text(AppStrings.get(R.string.ui_0ad23c38d9), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteProfileDialog = false }) {
          Text(AppStrings.get(R.string.ui_c1e7a1dd63))
        }
      }
    )
  }
}

@Composable
private fun SettingsConnectionEntry(onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("settings_connection_entry"),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.SettingsInputComponent,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = AppStrings.get(R.string.nav_connection),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = AppStrings.get(R.string.settings_connection_summary),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Icon(
        imageVector = Icons.Default.ArrowForward,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun SettingsSectionHeading(title: String, tag: String? = null) {
  Text(
    text = title,
    modifier = if (tag == null) Modifier else Modifier.testTag(tag),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

private enum class NotificationToggleType {
  CONFIG_CHANGES,
  DAILY_SUMMARY
}

@Composable
private fun NotificationSettingsSection(
  settings: NotificationSettings,
  onConfigChangeAlertsChanged: (Boolean) -> Unit,
  onDailySummaryChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    title = AppStrings.get(R.string.ui_fc2cbca9ac),
    subtitle = AppStrings.get(R.string.ui_c8d644e4b9),
    modifier = modifier
  ) {
    NextDnsSettingToggleRow(
      title = AppStrings.get(R.string.ui_d2a366ccf5),
      subtitle = AppStrings.get(R.string.ui_693b1184c2),
      checked = settings.configChangeAlertsEnabled,
      onCheckedChange = onConfigChangeAlertsChanged
    )

    HorizontalDivider(
      modifier = Modifier.padding(vertical = 4.dp),
      color = MaterialTheme.colorScheme.outlineVariant
    )

    NextDnsSettingToggleRow(
      title = AppStrings.get(R.string.ui_bb763d7bf3),
      subtitle = AppStrings.get(R.string.ui_4ac3ce8599),
      checked = settings.dailySummaryEnabled,
      onCheckedChange = onDailySummaryChanged
    )
  }
}

/**
 * 1. Görünüm Ayarları Bölümü
 */
@Composable
fun AppearanceSection(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val themePrefs = remember { ThemePreferences(context) }
  val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
  val scope = rememberCoroutineScope()

  NextDnsCard(title = AppStrings.get(R.string.ui_e1b0af0c14), modifier = modifier) {
    SingleChoiceSegmentedButtonRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp)
    ) {
      ThemeMode.entries.forEachIndexed { index, mode ->
        SegmentedButton(
          selected = (themeMode == mode),
          onClick = { scope.launch { themePrefs.setThemeMode(mode) } },
          shape = SegmentedButtonDefaults.itemShape(
            index = index,
            count = ThemeMode.entries.size
          ),
          label = {
            Text(
              text = when (mode) {
                ThemeMode.LIGHT -> AppStrings.get(R.string.ui_bbb1132f30)
                ThemeMode.DARK -> AppStrings.get(R.string.theme_dark)
                ThemeMode.SYSTEM -> AppStrings.get(R.string.theme_system)
              },
              maxLines = 1
            )
          }
        )
      }
    }
  }
}

/**
 * 3. Günlükler ve Gizlilik Ayarları Bölümü State Modeli
 */
@Immutable
data class LogsAndPrivacySectionState(
  val configSettings: ConfigSettings,
  val onToggleLogsEnabled: (Boolean) -> Unit,
  val onToggleLogClientIps: (Boolean) -> Unit,
  val onToggleLogDomains: (Boolean) -> Unit,
  val onSetLogRetention: (String) -> Unit,
  val onSetLogStorageLocation: (String) -> Unit,
  val onExportClick: () -> Unit,
  val onClearLogsClick: () -> Unit
)

/**
 * 3. Günlükler ve Gizlilik Ayarları Bölümü
 */
@Composable
fun LogsAndPrivacySection(
  state: LogsAndPrivacySectionState,
  modifier: Modifier = Modifier
) {
  val retentionOptions = listOf("6 saat", "1 gün", "1 hafta", "1 ay", "3 ay", "6 ay", "1 yıl", "2 yıl")
  val locationOptions = listOf("İsviçre (CH)", "Avrupa Birliği (AB)", "Amerika Birleşik Devletleri (ABD)")
  val configSettings = state.configSettings

  NextDnsCard(
    title = AppStrings.get(R.string.ui_469fe6b9ee),
    subtitle = AppStrings.get(R.string.ui_6d9044322d),
    modifier = modifier
  ) {
    // Günlükleri etkinleştir toggle
    NextDnsSettingToggleRow(
      title = AppStrings.get(R.string.ui_975fd0ef39),
      subtitle = AppStrings.get(R.string.ui_a29eef8cc4),
      checked = configSettings.logsEnabled,
      onCheckedChange = state.onToggleLogsEnabled
    )

    if (configSettings.logsEnabled) {
      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      Spacer(modifier = Modifier.height(10.dp))

      // IP & Domain kayıt onay kutuları
      NextDnsCheckboxRow(
        title = AppStrings.get(R.string.ui_4843d526ea),
        subtitle = AppStrings.get(R.string.ui_44bf716fa8),
        checked = configSettings.logClientIps,
        onCheckedChange = state.onToggleLogClientIps
      )

      NextDnsCheckboxRow(
        title = AppStrings.get(R.string.ui_ce0860f44d),
        subtitle = AppStrings.get(R.string.log_domains_description),
        checked = configSettings.logDomains,
        onCheckedChange = state.onToggleLogDomains
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Saklama Süresi ve Depolama Konumu Dropdown'ları
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        NextDnsDropdownSelector(
          label = AppStrings.get(R.string.ui_591abe76df),
          selectedValue = configSettings.logRetention,
          options = retentionOptions,
          onOptionSelected = state.onSetLogRetention,
          modifier = Modifier.weight(1f)
        )

        NextDnsDropdownSelector(
          label = AppStrings.get(R.string.ui_b9e7b41f06),
          selectedValue = configSettings.logStorageLocation,
          options = locationOptions,
          onOptionSelected = state.onSetLogStorageLocation,
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Aksiyon Butonları
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        NextDnsOutlineButton(
          text = AppStrings.get(R.string.ui_f57b70c49f),
          onClick = state.onExportClick,
          colors = NextDnsOutlineButtonColors(
            borderColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.primary
          ),
          icon = Icons.Default.Download,
          modifier = Modifier.weight(1f)
        )

        NextDnsOutlineButton(
          text = AppStrings.get(R.string.ui_a871d61ff5),
          onClick = state.onClearLogsClick,
          colors = NextDnsOutlineButtonColors(
            borderColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.error
          ),
          icon = Icons.Default.Delete,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

/**
 * Geriye dönük uyumluluk için LogsAndPrivacySection aşırı yüklemesi
 */
@Composable
fun LogsAndPrivacySection(
  configSettings: ConfigSettings,
  viewModel: NextDnsViewModel,
  onExportClick: () -> Unit,
  onClearLogsClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  LogsAndPrivacySection(
    state = LogsAndPrivacySectionState(
      configSettings = configSettings,
      onToggleLogsEnabled = { viewModel.toggleLogsEnabled(it) },
      onToggleLogClientIps = { viewModel.toggleLogClientIps(it) },
      onToggleLogDomains = { viewModel.toggleLogDomains(it) },
      onSetLogRetention = { viewModel.setLogRetention(it) },
      onSetLogStorageLocation = { viewModel.setLogStorageLocation(it) },
      onExportClick = onExportClick,
      onClearLogsClick = onClearLogsClick
    ),
    modifier = modifier
  )
}

/**
 * 4. Engel Sayfası Bölümü
 */
@Composable
fun BlockPageSection(
  blockPageEnabled: Boolean,
  onToggleBlockPage: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val uriHandler = LocalUriHandler.current

  NextDnsCard(
    title = AppStrings.get(R.string.ui_92ca542cb4),
    modifier = modifier
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .bounceClick { onToggleBlockPage(!blockPageEnabled) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        NextDnsSwitch(
          checked = blockPageEnabled,
          onCheckedChange = onToggleBlockPage
        )
        Text(
          text = AppStrings.get(R.string.ui_8e63c077a5),
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 13.sp
        )
      }

      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = AppStrings.get(R.string.ui_4af4c84b30),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
          )
          val annotatedText = buildAnnotatedString {
            pushStringAnnotation("ca", "https://nextdns.io/ca")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
              append("https://nextdns.io/ca")
            }
            pop()
            append(AppStrings.get(R.string.ui_f1aa098317))
            pushStringAnnotation("help", "https://help.nextdns.io/t/x2hmvas/how-to-install-and-trust-nextdns-root-ca")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
              append(AppStrings.get(R.string.help_here))
            }
            pop()
            append(AppStrings.get(R.string.help_end))
          }
          androidx.compose.foundation.text.ClickableText(
            text = annotatedText,
            style = TextStyle(
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp,
              lineHeight = 16.sp
            ),
            onClick = { offset ->
              annotatedText.getStringAnnotations("ca", offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) }
              annotatedText.getStringAnnotations("help", offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) }
            }
          )
        }
      }
    }
  }
}

/**
 * 5. Performans Bölümü
 */
@Composable
fun PerformanceSection(
  configSettings: ConfigSettings,
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    title = AppStrings.get(R.string.ui_559b742446),
    subtitle = AppStrings.get(R.string.ui_6d97d4726f),
    modifier = modifier
  ) {
    Column {
      EdnsSubnetBlock(
        enabled = configSettings.ednsClientSubnet,
        onToggle = { viewModel.toggleEdns(it) }
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

      CacheBoostBlock(
        enabled = configSettings.cacheBoost,
        onToggle = { viewModel.toggleCacheBoost(it) }
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

      CnameFlatteningBlock(
        enabled = configSettings.cnameFlattening,
        onToggle = { viewModel.toggleCnameFlattening(it) }
      )
    }
  }
}

@Composable
private fun EdnsSubnetBlock(
  enabled: Boolean,
  onToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = AppStrings.get(R.string.ui_fdfae18144),
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.Bold,
      fontSize = 13.5.sp
    )
    Text(
      text = AppStrings.get(R.string.ui_c115f79722),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 12.sp
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      NextDnsSwitch(
        checked = enabled,
        onCheckedChange = onToggle
      )
      Text(
        text = AppStrings.get(R.string.ui_31eea8dfbe),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp
      )
    }
  }
}

@Composable
private fun CacheBoostBlock(
  enabled: Boolean,
  onToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = AppStrings.get(R.string.ui_e5b3c1ddd0),
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.Bold,
      fontSize = 13.5.sp
    )
    Text(
      text = AppStrings.get(R.string.ui_77ad4a90bc),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 12.sp
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      NextDnsSwitch(
        checked = enabled,
        onCheckedChange = onToggle
      )
      Text(
        text = AppStrings.get(R.string.ui_320039c2a6),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp
      )
    }
  }
}

@Composable
private fun CnameFlatteningBlock(
  enabled: Boolean,
  onToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = AppStrings.get(R.string.ui_4eb09dec40),
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.Bold,
      fontSize = 13.5.sp
    )
    Text(
      text = AppStrings.get(R.string.ui_9cb9b3f307),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 12.sp
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      NextDnsSwitch(
        checked = enabled,
        onCheckedChange = onToggle
      )
      Text(
        text = AppStrings.get(R.string.ui_d1121a66b2),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 12.sp
      )
    }
  }
}

/**
 * 7. Web3 Bölümü
 */
@Composable
fun Web3Section(
  web3Enabled: Boolean,
  onToggleWeb3: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    title = "Web3",
    isBeta = true,
    subtitle = AppStrings.get(R.string.web3_description),
    modifier = modifier
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
      // Sağlayıcı Logoları
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(color = MaterialTheme.colorScheme.onSurface, shape = CircleShape, modifier = Modifier.size(40.dp)) {
          Box(contentAlignment = Alignment.Center) {
            Text("ENS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
          }
        }
        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(40.dp)) {
          Box(contentAlignment = Alignment.Center) {
            Text("UD", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
          }
        }
        Surface(color = MaterialTheme.colorScheme.onSurface, shape = CircleShape, modifier = Modifier.size(40.dp)) {
          Box(contentAlignment = Alignment.Center) {
            Text("HNS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
          }
        }
        Surface(color = MaterialTheme.colorScheme.tertiary, shape = CircleShape, modifier = Modifier.size(40.dp)) {
          Box(contentAlignment = Alignment.Center) {
            Text("IPFS", color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

      // Etkinleştirme Anahtarı
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .bounceClick { onToggleWeb3(!web3Enabled) }
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = AppStrings.get(R.string.ui_8fa74dac7d),
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Bold,
          fontSize = 13.5.sp
        )
        NextDnsSwitch(
          checked = web3Enabled,
          onCheckedChange = onToggleWeb3
        )
      }
    }
  }
}

/**
 * 9. Profil Eylemleri Bölümü (Kopyala / Sil)
 */
@Composable
fun ProfileActionsSection(
  profileName: String,
  onDeleteProfile: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    NextDnsActionCard(
      buttonText = AppStrings.get(R.string.delete_profile_button, profileName),
      description = AppStrings.get(R.string.ui_df88ac14a6),
      onButtonClick = onDeleteProfile,
      isDanger = true
    )
  }
}


