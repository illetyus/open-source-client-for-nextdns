package com.example.ui.screens

import com.example.R
import com.example.data.model.normalizeDomainRule
import com.example.i18n.AppStrings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NextDnsViewModel

@Composable
fun DenylistScreen(
  viewModel: NextDnsViewModel,
  modifier: Modifier = Modifier
) {
  val denylist by viewModel.denylist.collectAsStateWithLifecycle()
  var domainInput by remember { mutableStateOf("") }
  var isError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  val context = androidx.compose.ui.platform.LocalContext.current

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      NextDnsCard(
        title = AppStrings.get(R.string.ui_3490101f8d),
        subtitle = AppStrings.get(R.string.ui_64d2b316b6)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.Top
        ) {
          OutlinedTextField(
            value = domainInput,
            onValueChange = { 
              domainInput = it
              isError = false
            },
            placeholder = {
              Text(AppStrings.get(R.string.ui_24c0e0a2c6), color = MaterialTheme.colorScheme.outline, fontSize = 12.5.sp)
            },
            singleLine = true,
            isError = isError,
            trailingIcon = {
              if (isError) {
                Icon(Icons.Default.Warning, contentDescription = AppStrings.get(R.string.ui_307b9dc791), tint = MaterialTheme.colorScheme.error)
              }
            },
            supportingText = {
              if (isError) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
              }
            },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
              errorBorderColor = MaterialTheme.colorScheme.error,
              errorTrailingIconColor = MaterialTheme.colorScheme.error,
              focusedTextColor = MaterialTheme.colorScheme.onSurface,
              unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              errorContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
          )

          NextDnsButton(
            text = AppStrings.get(R.string.ui_422579fc64),
            onClick = {
              val submittedText = domainInput
              val input = normalizeDomainRule(submittedText)
              if (input != null) {
                viewModel.addToDenylist(input) {
                  if (domainInput == submittedText) domainInput = ""
                }
                isError = false
              } else {
                isError = true
                if (!submittedText.contains(".")) {
                  errorMessage = AppStrings.get(R.string.ui_c10eecdcb0)
                } else {
                  errorMessage = AppStrings.get(R.string.ui_585a0d389f)
                }
              }
            },
            modifier = Modifier.padding(top = 8.dp)
          )
        }
      }
    }

    if (denylist.isNotEmpty()) {
      items(denylist, key = { it.id }) { item ->
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(14.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .width(3.5.dp)
                  .height(30.dp)
                  .background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
              )
              
              FaviconImage(domain = item.domain, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
              )
              
              Text(
                text = item.domain,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
              )
            }

            Row(
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              NextDnsSwitch(
                checked = item.active,
                onCheckedChange = { viewModel.toggleDenylistItem(item.id) }
              )
              IconButton(
                onClick = { viewModel.removeFromDenylist(item.id) },
                modifier = Modifier.size(48.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = AppStrings.get(R.string.ui_f698e9b40f),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}
