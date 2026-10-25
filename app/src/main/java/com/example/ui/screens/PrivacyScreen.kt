package com.example.ui.screens

import com.example.R
import com.example.i18n.AppStrings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BlocklistEntry
import com.example.data.model.NativeTrackingEntry
import com.example.data.model.PrivacySettings
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NextDnsViewModel

@Composable
fun PrivacyScreen(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val settings by viewModel.privacySettings.collectAsStateWithLifecycle()
  var showAddBlocklistDialog by remember { mutableStateOf(false) }
  var showAddNativeDialog by remember { mutableStateOf(false) }

  val activeBlocklists = settings.blocklists.filter { it.active }
  val activeNatives = settings.nativeTracking.filter { it.active }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .testTag("privacy_settings_list"),
    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      BlocklistsSection(
        activeBlocklists = activeBlocklists,
        onToggleBlocklist = { viewModel.toggleBlocklist(it) },
        onOpenAddDialog = { showAddBlocklistDialog = true }
      )
    }

    item {
      NativeTrackingSection(
        activeNatives = activeNatives,
        onToggleNative = { viewModel.toggleNativeTracking(it) },
        onOpenAddDialog = { showAddNativeDialog = true }
      )
    }

    item {
      DisguisedTrackersSection(
        disguisedTrackers = settings.disguisedTrackers,
        onToggleDisguisedTrackers = { viewModel.toggleDisguisedTrackers(it) }
      )
    }

    item {
      AffiliatesSection(
        allowAffiliates = settings.allowAffiliates,
        onToggleAllowAffiliates = { viewModel.toggleAllowAffiliates(it) }
      )
    }
  }

  if (showAddBlocklistDialog) {
    AddBlocklistCatalogDialog(
      blocklists = settings.blocklists,
      onToggleBlocklist = { viewModel.toggleBlocklist(it) },
      onDismiss = { showAddBlocklistDialog = false }
    )
  }

  if (showAddNativeDialog) {
    AddNativeTrackingDialog(
      nativeTrackings = settings.nativeTracking,
      onToggleNative = { viewModel.toggleNativeTracking(it) },
      onDismiss = { showAddNativeDialog = false }
    )
  }
}

// =========================================================================
// Modular Screen Sections
// =========================================================================

@Composable
private fun BlocklistsSection(
  activeBlocklists: List<BlocklistEntry>,
  onToggleBlocklist: (String) -> Unit,
  onOpenAddDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_6ef8c92a60),
    subtitle = AppStrings.get(R.string.ui_7c705b3b35)
  ) {
    if (activeBlocklists.isNotEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        activeBlocklists.forEach { item ->
          BlocklistItemCard(item = item, onRemove = { onToggleBlocklist(item.id) })
        }
      }
    } else {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp)
      ) {
        Text(
          text = AppStrings.get(R.string.ui_12e04ab891),
          color = MaterialTheme.colorScheme.outline,
          fontSize = 12.sp,
          modifier = Modifier.padding(12.dp)
        )
      }
    }

    NextDnsButton(
      text = AppStrings.get(R.string.manage_blocklists),
      onClick = onOpenAddDialog,
      icon = Icons.Default.Add
    )
  }
}

@Composable
private fun BlocklistItemCard(
  item: BlocklistEntry,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .width(3.5.dp)
            .height(42.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = item.name,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold,
              fontSize = 13.5.sp
            )
            Surface(
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
              shape = RoundedCornerShape(4.dp)
            ) {
              Text(
                text = item.category,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
          Text(
            text = item.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 15.sp
          )
          if (!item.website.isNullOrBlank()) {
            Text(
              text = item.website,
              color = MaterialTheme.colorScheme.primary,
              fontSize = 11.sp
            )
          }
          val updateInfo = if (item.updatedTime.isNotBlank()) AppStrings.get(R.string.last_updated, item.updatedTime) else AppStrings.get(R.string.ui_f4435d51ea)
          val formattedEntries = try {
            java.text.NumberFormat.getInstance(AppStrings.locale).format(item.entriesCount)
          } catch (_: Exception) {
            item.entriesCount.toString()
          }
          Text(
            text = AppStrings.get(R.string.rules_updated, formattedEntries, updateInfo),
            color = MaterialTheme.colorScheme.outline,
            fontSize = 10.5.sp
          )
        }
      }

      IconButton(
        onClick = onRemove,
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = AppStrings.get(R.string.ui_b88019aa28),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
private fun NativeTrackingSection(
  activeNatives: List<NativeTrackingEntry>,
  onToggleNative: (String) -> Unit,
  onOpenAddDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_cc7f654b6c),
    subtitle = AppStrings.get(R.string.ui_4efc38ead6),
    isBeta = true
  ) {
    if (activeNatives.isNotEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        activeNatives.forEach { nat ->
          NativeTrackingItemCard(nat = nat, onRemove = { onToggleNative(nat.id) })
        }
      }
    }

    NextDnsButton(
      text = AppStrings.get(R.string.ui_eaa29349bc),
      onClick = onOpenAddDialog,
      icon = Icons.Default.Add
    )
  }
}

@Composable
private fun NativeTrackingItemCard(
  nat: NativeTrackingEntry,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier
) {
  val domain = nativeTrackingFaviconDomain(nat.id).orEmpty()

  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .width(3.5.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        FaviconImage(
          domain = domain,
          fallbackLabel = nat.name,
          modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .testTag("native_tracking_icon_${nat.id}")
        )
        Column {
          Text(
            text = nat.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
          Text(
            text = nat.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
          )
        }
      }

      IconButton(
        onClick = onRemove,
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = AppStrings.get(R.string.ui_b88019aa28),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
private fun DisguisedTrackersSection(
  disguisedTrackers: Boolean,
  onToggleDisguisedTrackers: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsSettingToggle(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_f666e1ea8a),
    subtitle = AppStrings.get(R.string.ui_48ef0bc17d),
    checked = disguisedTrackers,
    onCheckedChange = onToggleDisguisedTrackers
  )
}

@Composable
private fun AffiliatesSection(
  allowAffiliates: Boolean,
  onToggleAllowAffiliates: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_1eabea1802),
    subtitle = AppStrings.get(R.string.ui_33bf65ec66)
  ) {
    Surface(
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp)
    ) {
      Row(
        modifier = Modifier.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Lock,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = AppStrings.get(R.string.ui_a425a01661),
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .bounceClick { onToggleAllowAffiliates(!allowAffiliates) }
        .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = if (allowAffiliates) AppStrings.get(R.string.ui_6216cb4919) else AppStrings.get(R.string.ui_28fcc3af77),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        color = if (allowAffiliates) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
      )
      NextDnsSwitch(
        checked = allowAffiliates,
        onCheckedChange = onToggleAllowAffiliates
      )
    }
  }
}

// =========================================================================
// Dialogs
// =========================================================================

@Composable
private fun AddBlocklistCatalogDialog(
  blocklists: List<BlocklistEntry>,
  onToggleBlocklist: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(AppStrings.get(R.string.ui_f43a65ad82)) }
  val categories = listOf(AppStrings.get(R.string.ui_f43a65ad82), AppStrings.get(R.string.ui_0f1322006d), AppStrings.get(R.string.ui_bde6632ed8), "Gizlilik", AppStrings.get(R.string.ui_5ed0130b25))

  val filteredList = blocklists.filter { entry ->
    val matchesCat = selectedCategory == AppStrings.get(R.string.ui_f43a65ad82) || (entry.category ?: AppStrings.get(R.string.ui_0f1322006d)).equals(selectedCategory, ignoreCase = true)
    val matchesQuery = searchQuery.isBlank() ||
      entry.name.contains(searchQuery, ignoreCase = true) ||
      entry.description.contains(searchQuery, ignoreCase = true)
    matchesCat && matchesQuery
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(AppStrings.get(R.string.ui_18a8812db5), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text(AppStrings.get(R.string.ui_5197bf4e5f), color = MaterialTheme.colorScheme.outline, fontSize = 12.sp) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
          ),
          shape = RoundedCornerShape(10.dp)
        )

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(categories) { cat ->
            val isSelected = selectedCategory == cat
            Surface(
              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.clickable { selectedCategory = cat }
            ) {
              Text(
                text = cat,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              )
            }
          }
        }
      }
    },
    text = {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 380.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(filteredList, key = { it.id }) { catItem ->
          val isAdded = catItem.active
          Surface(
            color = if (isAdded) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
              1.dp,
              if (isAdded) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.Top,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = catItem.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                  ) {
                    Text(
                      text = catItem.category,
                      color = MaterialTheme.colorScheme.primary,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                  }
                }
                Text(
                  text = catItem.description,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 11.sp,
                  maxLines = 2,
                  modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                val formattedEntries = try {
                  java.text.NumberFormat.getInstance(AppStrings.locale).format(catItem.entriesCount)
                } catch (_: Exception) {
                  catItem.entriesCount.toString()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = AppStrings.get(R.string.entries_count, formattedEntries),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                  )
                  if (catItem.updatedTime.isNotBlank()) {
                    Text(
                      text = AppStrings.get(R.string.catalog_updated, catItem.updatedTime),
                      color = MaterialTheme.colorScheme.outline,
                      fontSize = 10.sp
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.width(8.dp))

              Button(
                onClick = { onToggleBlocklist(catItem.id) },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isAdded) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                  contentColor = if (isAdded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
              ) {
                Text(
                  text = if (isAdded) AppStrings.get(R.string.ui_b88019aa28) else "Ekle",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("privacy_blocklist_dialog_close")) {
        Text(AppStrings.get(R.string.ui_79c4b04b6e), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {}
  )
}

@Composable
private fun AddNativeTrackingDialog(
  nativeTrackings: List<NativeTrackingEntry>,
  onToggleNative: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val inactiveNatives = nativeTrackings.filter { !it.active }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(18.dp),
    title = {
      Text(AppStrings.get(R.string.ui_4ba62c96ff), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    },
    text = {
      if (inactiveNatives.isEmpty()) {
        Text(AppStrings.get(R.string.ui_fb11f587fe), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          inactiveNatives.forEach { nat ->
            val domain = nativeTrackingFaviconDomain(nat.id).orEmpty()

            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier
                .fillMaxWidth()
                .bounceClick {
                  onToggleNative(nat.id)
                  onDismiss()
                }
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  FaviconImage(
                    domain = domain,
                    fallbackLabel = nat.name,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                  )
                  Column {
                    Text(nat.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(nat.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                  }
                }
                Icon(Icons.Default.Add, contentDescription = AppStrings.get(R.string.ui_f560a85f28), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("privacy_native_tracking_dialog_close")) {
        Text(AppStrings.get(R.string.close), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  )
}
