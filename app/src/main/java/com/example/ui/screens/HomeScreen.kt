package com.example.ui.screens

import androidx.compose.ui.res.painterResource

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.i18n.AppStrings
import com.example.ui.components.NextDnsButton
import com.example.ui.components.StatusBeacon
import com.example.ui.components.bounceClick
import com.example.ui.theme.*
import com.example.ui.viewmodel.NavTab
import com.example.ui.viewmodel.NextDnsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val currentSectionSyncState by viewModel.currentSectionSyncState.collectAsStateWithLifecycle()
  val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
  val profiles by viewModel.profiles.collectAsStateWithLifecycle()
  val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

  var showProfileMenu by remember { mutableStateOf(false) }
  var showNewProfileDialog by remember { mutableStateOf(false) }
  var newProfileNameInput by remember { mutableStateOf("") }
  val snackbarHostState = remember { SnackbarHostState() }
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> viewModel.startForegroundProfileSync()
        Lifecycle.Event.ON_PAUSE -> viewModel.stopForegroundProfileSync()
        else -> Unit
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      viewModel.startForegroundProfileSync()
    }

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.stopForegroundProfileSync()
    }
  }

  DisposableEffect(lifecycleOwner, currentTab) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> viewModel.startVisibleTabSync(currentTab)
        Lifecycle.Event.ON_PAUSE -> viewModel.stopVisibleTabSync()
        else -> Unit
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      viewModel.startVisibleTabSync(currentTab)
    }

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.stopVisibleTabSync()
    }
  }

  LaunchedEffect(uiMessage) {
    uiMessage?.let {
      snackbarHostState.showSnackbar(
        message = it.text,
        duration = SnackbarDuration.Short
      )
      viewModel.dismissMessage()
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val navigationMode = navigationLayoutForWidth(maxWidth.value)

    Scaffold(
      modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.statusBarsPadding()
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Independent client title, profile selector and connection shortcut.
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Allow the full product title to wrap above the profile selector.
            Column(
              modifier = Modifier.weight(1f).padding(end = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Project-authored network symbol and refresh status.
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .heightIn(min = 48.dp)
                  .bounceClick(scaleDown = 0.97f) {
                    viewModel.syncAllData()
                  }
                  .padding(horizontal = 4.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  StatusBeacon(
                    color = MaterialTheme.colorScheme.primary,
                    size = 12.dp,
                    isPulsing = currentSectionSyncState?.isRefreshing == true || currentSectionSyncState?.isSaving == true
                  )
                  Icon(
                    painter = painterResource(R.drawable.ic_client_network),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                  )
                }
                Text(
                  text = stringResource(R.string.app_name),
                  modifier = Modifier.weight(1f),
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.4).sp
                  ),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }

              // Profile Selector Dropdown Box
              Box {
                Surface(
                  modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .heightIn(min = 48.dp)
                    .bounceClick(scaleDown = 0.97f) { showProfileMenu = true },
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                    )
                    Text(
                      text = activeProfile?.name ?: AppStrings.get(R.string.ui_18d49619ba),
                      style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                      ),
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.widthIn(max = 110.dp)
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
                  expanded = showProfileMenu,
                  onDismissRequest = { showProfileMenu = false },
                  modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                  profiles.forEach { prof ->
                    val isCurrent = prof.id == activeProfile?.id
                    DropdownMenuItem(
                      text = {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Column {
                            Text(
                              text = prof.name,
                              color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                              fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                              fontSize = 13.sp
                            )
                            Text(
                              text = "ID: ${prof.id}",
                              color = MaterialTheme.colorScheme.outline,
                              fontSize = 10.sp
                            )
                          }
                          if (isCurrent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                              imageVector = Icons.Default.Check,
                              contentDescription = AppStrings.get(R.string.ui_cab5ee48fc),
                              tint = MaterialTheme.colorScheme.primary,
                              modifier = Modifier.size(14.dp)
                            )
                          }
                        }
                      },
                      onClick = {
                        viewModel.switchProfile(prof.id)
                        showProfileMenu = false
                      }
                    )
                  }
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                  DropdownMenuItem(
                    text = {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Icon(
                          imageVector = Icons.Default.Add,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(14.dp)
                        )
                        Text(
                          text = AppStrings.get(R.string.ui_cff3a955d7),
                          color = MaterialTheme.colorScheme.primary,
                          fontSize = 12.sp,
                          fontWeight = FontWeight.Bold
                        )
                      }
                    },
                    onClick = {
                      showProfileMenu = false
                      showNewProfileDialog = true
                    }
                  )
                }
              }
            }

            IconButton(
              onClick = { viewModel.selectTab(NavTab.ACCOUNT) },
              modifier = Modifier
                .size(48.dp)
                .testTag("home_nextdns_connection_button")
            ) {
              Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = AppStrings.get(R.string.account_profile_action),
                tint = if (currentTab == NavTab.ACCOUNT) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
              )
            }

          }

          if (navigationMode == NavigationLayoutMode.COMPACT) {
          // --- Android 16 Expressive Segmented Navigation Tabs ---
          val tabScrollState = rememberScrollState()
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(tabScrollState)
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
              nextDnsNavigationGroups.flatMap { it.tabs }.forEach { tab ->
              val isSelected = currentTab == tab
              val tabBgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "tabBg"
              )
              val tabBorderColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "tabBorder"
              )

              Surface(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .border(1.dp, tabBorderColor, RoundedCornerShape(12.dp))
                  .heightIn(min = 48.dp)
                  .selectable(
                    selected = isSelected,
                    role = Role.Tab,
                    onClick = { viewModel.selectTab(tab) }
                  )
                  .testTag("tab_${tab.name.lowercase()}"),
                color = tabBgColor,
                shape = RoundedCornerShape(12.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = getTabIcon(tab),
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                  )
                  Text(
                    text = tab.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                      fontSize = 12.sp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          }

          currentSectionSyncState?.let { syncState ->
            val syncText = when {
              syncState.isSaving -> AppStrings.get(R.string.saving_server)
              syncState.isRefreshing -> AppStrings.get(R.string.ui_a3b98b8a79)
              syncState.errorMessage != null -> {
                val lastOk = syncState.lastSuccessAt?.let(::formatSyncTime) ?: AppStrings.get(R.string.none)
                AppStrings.get(R.string.sync_failed_time, lastOk)
              }
              syncState.lastSuccessAt != null -> AppStrings.get(R.string.sync_current_time, formatSyncTime(syncState.lastSuccessAt))
              else -> AppStrings.get(R.string.ui_a68ae936eb)
            }

            val syncColor = when {
              syncState.errorMessage != null -> MaterialTheme.colorScheme.error
              syncState.isSaving || syncState.isRefreshing -> MaterialTheme.colorScheme.primary
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                  contentDescription = syncText
                  liveRegion = LiveRegionMode.Polite
                }
                .padding(horizontal = 16.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              if (syncState.isSaving || syncState.isRefreshing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(12.dp),
                  strokeWidth = 1.5.dp,
                  color = MaterialTheme.colorScheme.primary
                )
              } else {
                Icon(
                  imageVector = if (syncState.errorMessage != null) Icons.Default.Warning else Icons.Default.CloudDone,
                  contentDescription = null,
                  tint = syncColor,
                  modifier = Modifier.size(13.dp)
                )
              }
              Text(
                text = syncText,
                color = syncColor,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        }
      }
    },
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(16.dp)
      ) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = MaterialTheme.colorScheme.onSurface,
          shape = RoundedCornerShape(14.dp)
        )
      }
    }
  ) { paddingValues ->
    Row(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(paddingValues)
    ) {
      when (navigationMode) {
        NavigationLayoutMode.COMPACT -> Unit
        NavigationLayoutMode.MEDIUM -> MediumNavigationRail(
          currentTab = currentTab,
          onTabSelected = viewModel::selectTab
        )
        NavigationLayoutMode.EXPANDED -> ExpandedNavigationSidebar(
          currentTab = currentTab,
          onTabSelected = viewModel::selectTab
        )
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      ) {
        AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
          if (targetState.ordinal > initialState.ordinal) {
            (slideInHorizontally { width -> width / 3 } + fadeIn(animationSpec = tween(220)))
              .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut(animationSpec = tween(180)))
          } else {
            (slideInHorizontally { width -> -width / 3 } + fadeIn(animationSpec = tween(220)))
              .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut(animationSpec = tween(180)))
          }
        },
        label = "NextDnsTabContent"
      ) { tab ->
        when (tab) {
          NavTab.SETUP -> SetupScreen(viewModel)
          NavTab.SECURITY -> SecurityScreen(viewModel)
          NavTab.PRIVACY -> PrivacyScreen(viewModel)
          NavTab.PARENTAL -> ParentalScreen(viewModel)
          NavTab.DENYLIST -> DenylistScreen(viewModel)
          NavTab.ALLOWLIST -> AllowlistScreen(viewModel)
          NavTab.ANALYTICS -> AnalyticsScreen(viewModel)
          NavTab.LOGS -> LogsScreen(viewModel)
          NavTab.SETTINGS -> SettingsScreen(viewModel)
          NavTab.ACCOUNT -> AccountScreen(viewModel)
        }
        }
      }
    }
  }
  }

  // Dialog: Yeni Profil Oluştur
  if (showNewProfileDialog) {
    AlertDialog(
      onDismissRequest = { showNewProfileDialog = false },
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(18.dp),
      title = {
        Text(
          text = AppStrings.get(R.string.ui_cff3a955d7),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = AppStrings.get(R.string.ui_bc16686dc9),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
          )
          OutlinedTextField(
            value = newProfileNameInput,
            onValueChange = { newProfileNameInput = it },
            placeholder = { Text(AppStrings.get(R.string.ui_7abc30d137), color = MaterialTheme.colorScheme.outline, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
              focusedTextColor = MaterialTheme.colorScheme.onSurface,
              unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(12.dp)
          )
        }
      },
      confirmButton = {
        NextDnsButton(
          text = AppStrings.get(R.string.ui_89c75b03b9),
          onClick = {
            if (newProfileNameInput.isNotBlank()) {
              viewModel.createProfile(newProfileNameInput.trim())
              newProfileNameInput = ""
              showNewProfileDialog = false
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showNewProfileDialog = false }) {
          Text(AppStrings.get(R.string.ui_7227874813), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
      }
    )
  }
}

@Composable
private fun MediumNavigationRail(
  currentTab: NavTab,
  onTabSelected: (NavTab) -> Unit
) {
  Surface(
    modifier = Modifier
      .width(88.dp)
      .fillMaxHeight(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 8.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      nextDnsNavigationGroups
        .flatMap { it.tabs }
        .forEach { tab ->
          val selected = currentTab == tab
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 56.dp)
              .clip(RoundedCornerShape(14.dp))
              .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { onTabSelected(tab) }
              )
              .testTag("rail_${tab.name.lowercase()}")
              .background(
                if (selected) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  Color.Transparent
                }
              )
              .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = getTabIcon(tab),
              contentDescription = null,
              tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = tab.title,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
    }
  }
}

@Composable
private fun ExpandedNavigationSidebar(
  currentTab: NavTab,
  onTabSelected: (NavTab) -> Unit
) {
  Surface(
    modifier = Modifier
      .width(236.dp)
      .fillMaxHeight(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      nextDnsNavigationGroups.forEachIndexed { groupIndex, group ->
        if (groupIndex > 0) {
          Spacer(modifier = Modifier.height(6.dp))
        }

        Text(
          text = group.title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        group.tabs.forEach { tab ->
          NavigationDrawerItem(
            label = {
              Text(
                text = tab.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            selected = currentTab == tab,
            onClick = { onTabSelected(tab) },
            icon = {
              Icon(
                imageVector = getTabIcon(tab),
                contentDescription = null
              )
            },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("drawer_${tab.name.lowercase()}"),
            shape = RoundedCornerShape(14.dp)
          )
        }
      }
    }
  }
}

private fun formatSyncTime(epochMillis: Long): String =
  java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    .format(java.util.Date(epochMillis))

private fun getTabIcon(tab: NavTab): ImageVector {
  return when (tab) {
    NavTab.SETUP -> Icons.Default.SettingsInputComponent
    NavTab.SECURITY -> Icons.Default.Security
    NavTab.PRIVACY -> Icons.Default.Shield
    NavTab.PARENTAL -> Icons.Default.FamilyRestroom
    NavTab.DENYLIST -> Icons.Default.Block
    NavTab.ALLOWLIST -> Icons.Default.CheckCircle
    NavTab.ANALYTICS -> Icons.Default.BarChart
    NavTab.LOGS -> Icons.Default.Article
    NavTab.SETTINGS -> Icons.Default.Settings
    NavTab.ACCOUNT -> Icons.Default.AccountCircle
  }
}

