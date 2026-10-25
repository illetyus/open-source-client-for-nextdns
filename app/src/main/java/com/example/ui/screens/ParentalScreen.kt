package com.example.ui.screens

import com.example.R
import com.example.i18n.AppStrings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BlockedCategoryEntry
import com.example.data.model.BlockedServiceEntry
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NextDnsViewModel

@Composable
fun ParentalScreen(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val settings by viewModel.parentalControlSettings.collectAsStateWithLifecycle()
  var showAddServiceDialog by remember { mutableStateOf(false) }
  var showAddCategoryDialog by remember { mutableStateOf(false) }

  val activeServices = settings.services.filter { it.active }
  val activeCategories = settings.categories.filter { it.active }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .testTag("parental_settings_list"),
    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      ParentalServicesSection(
        activeServices = activeServices,
        onToggleService = { viewModel.toggleParentalService(it) },
        onOpenAddDialog = { showAddServiceDialog = true }
      )
    }

    item {
      ParentalCategoriesSection(
        activeCategories = activeCategories,
        onToggleCategory = { viewModel.toggleParentalCategory(it) },
        onOpenAddDialog = { showAddCategoryDialog = true }
      )
    }

    item {
      ParentalSafeSearchSection(
        safeSearch = settings.safeSearch,
        onToggleSafeSearch = { viewModel.setSafeSearch(it) }
      )
    }

    item {
      ParentalYoutubeSection(
        youtubeRestricted = settings.youtubeRestrictedMode,
        onToggleYoutubeRestricted = { viewModel.setYoutubeRestricted(it) }
      )
    }

    item {
      ParentalBypassSection(
        blockBypass = settings.blockBypass,
        onToggleBlockBypass = { viewModel.setBlockBypass(it) }
      )
    }
  }

  if (showAddServiceDialog) {
    AddParentalServiceDialog(
      services = settings.services,
      onToggleService = { viewModel.toggleParentalService(it) },
      onDismiss = { showAddServiceDialog = false }
    )
  }

  if (showAddCategoryDialog) {
    AddParentalCategoryDialog(
      categories = settings.categories,
      onToggleCategory = { viewModel.toggleParentalCategory(it) },
      onDismiss = { showAddCategoryDialog = false }
    )
  }

}

// =========================================================================
// Modular Screen Sections
// =========================================================================

@Composable
private fun ParentalServicesSection(
  activeServices: List<BlockedServiceEntry>,
  onToggleService: (String) -> Unit,
  onOpenAddDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_86e9f0e4ce),
    subtitle = AppStrings.get(R.string.ui_43bc237560)
  ) {
    if (activeServices.isNotEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        activeServices.forEach { service ->
          ParentalServiceItemCard(
            service = service,
            onToggle = { onToggleService(service.id) },
            onRemove = { onToggleService(service.id) }
          )
        }
      }
    }

    NextDnsButton(
      text = AppStrings.get(R.string.ui_b02d56c853),
      onClick = onOpenAddDialog,
      icon = Icons.Default.Add
    )
  }
}

@Composable
private fun ParentalServiceItemCard(
  service: BlockedServiceEntry,
  onToggle: (Boolean) -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier
) {
  val domain = parentalServiceFaviconDomain(service.id, service.website).orEmpty()

  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .width(3.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FaviconImage(
            domain = domain,
            fallbackLabel = service.name,
            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
          )
          Text(
            text = service.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        NextDnsSwitch(
          checked = service.active,
          onCheckedChange = onToggle
        )
        IconButton(
          onClick = onRemove,
          modifier = Modifier.size(48.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = AppStrings.get(R.string.ui_b88019aa28),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun ParentalCategoriesSection(
  activeCategories: List<BlockedCategoryEntry>,
  onToggleCategory: (String) -> Unit,
  onOpenAddDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsCard(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_e9371667ba),
    subtitle = AppStrings.get(R.string.ui_c1c678372b)
  ) {
    if (activeCategories.isNotEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        activeCategories.forEach { category ->
          ParentalCategoryItemCard(
            category = category,
            onToggle = { onToggleCategory(category.id) },
            onRemove = { onToggleCategory(category.id) }
          )
        }
      }
    }

    NextDnsButton(
      text = AppStrings.get(R.string.ui_c216d0fa85),
      onClick = onOpenAddDialog,
      icon = Icons.Default.Add
    )
  }
}

@Composable
private fun ParentalCategoryItemCard(
  category: BlockedCategoryEntry,
  onToggle: (Boolean) -> Unit,
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
        .padding(horizontal = 14.dp, vertical = 10.dp),
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
            .width(3.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
        )
        Column {
          Text(
            text = category.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
          )
          Text(
            text = category.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        NextDnsSwitch(
          checked = category.active,
          onCheckedChange = onToggle
        )
        IconButton(
          onClick = onRemove,
          modifier = Modifier.size(48.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = AppStrings.get(R.string.ui_b88019aa28),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun ParentalSafeSearchSection(
  safeSearch: Boolean,
  onToggleSafeSearch: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsSettingToggle(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_ce261422e7),
    subtitle = AppStrings.get(R.string.ui_ffca076206),
    checked = safeSearch,
    onCheckedChange = onToggleSafeSearch
  )
}

@Composable
private fun ParentalYoutubeSection(
  youtubeRestricted: Boolean,
  onToggleYoutubeRestricted: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsSettingToggle(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_692c4c985a),
    subtitle = AppStrings.get(R.string.ui_6456a6c39a),
    checked = youtubeRestricted,
    onCheckedChange = onToggleYoutubeRestricted
  )
}

@Composable
private fun ParentalBypassSection(
  blockBypass: Boolean,
  onToggleBlockBypass: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  NextDnsSettingToggle(
    modifier = modifier,
    title = AppStrings.get(R.string.ui_5d3f711698),
    subtitle = AppStrings.get(R.string.ui_4b376be2ad),
    checked = blockBypass,
    onCheckedChange = onToggleBlockBypass
  )
}

// =========================================================================
// Dialogs
// =========================================================================

@Composable
private fun AddParentalServiceDialog(
  services: List<BlockedServiceEntry>,
  onToggleService: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  val inactiveServices = services
    .filter { !it.active }
    .filter { searchQuery.isBlank() || it.name.contains(searchQuery.trim(), ignoreCase = true) || it.id.contains(searchQuery.trim(), ignoreCase = true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(18.dp),
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(AppStrings.get(R.string.block_app_game), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text(AppStrings.get(R.string.ui_cf482d0257), fontSize = 12.sp) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp)
        )
      }
    },
    text = {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 350.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(inactiveServices) { srv ->
          val domain = parentalServiceFaviconDomain(srv.id, srv.website).orEmpty()
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
              .fillMaxWidth()
              .bounceClick {
                onToggleService(srv.id)
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
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FaviconImage(
                  domain = domain,
                  fallbackLabel = srv.name,
                  modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                )
                Text(srv.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
              }
              Icon(Icons.Default.Add, contentDescription = AppStrings.get(R.string.ui_f560a85f28), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(AppStrings.get(R.string.close), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  )
}

@Composable
private fun AddParentalCategoryDialog(
  categories: List<BlockedCategoryEntry>,
  onToggleCategory: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  val inactiveCats = categories
    .filter { !it.active }
    .filter { searchQuery.isBlank() || it.name.contains(searchQuery.trim(), ignoreCase = true) || it.description.contains(searchQuery.trim(), ignoreCase = true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(18.dp),
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(AppStrings.get(R.string.ui_fc9e7ea43b), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text(AppStrings.get(R.string.ui_559e575b15), fontSize = 12.sp) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp)
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        inactiveCats.forEach { cat ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
              .fillMaxWidth()
              .bounceClick {
                onToggleCategory(cat.id)
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
              Column {
                Text(cat.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(cat.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
              }
              Icon(Icons.Default.Add, contentDescription = AppStrings.get(R.string.ui_f560a85f28), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(AppStrings.get(R.string.close), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  )
}


