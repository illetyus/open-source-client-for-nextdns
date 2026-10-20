package com.example.ui.screens

import com.example.R
import com.example.i18n.AppStrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalePreferences
import com.example.ui.components.LanguagePicker
import com.example.data.legal.LegalDocuments

private data class LegalLabels(
  val title: String,
  val explanation: String,
  val terms: String,
  val privacy: String,
  val accept: String,
  val continueLabel: String,
  val error: String
)

private fun legalLabels(): LegalLabels = LegalLabels(
  AppStrings.get(R.string.ui_d451294fda), AppStrings.get(R.string.ui_b8a9f44998),
  AppStrings.get(R.string.ui_d451294fda), AppStrings.get(R.string.ui_ac96fa3c88),
  AppStrings.get(R.string.ui_7382c39e80), AppStrings.get(R.string.ui_7935969625),
  AppStrings.get(R.string.ui_3eb5f14831)
)

/**
 * Offline terms/privacy reader and explicit terms-only acceptance gate.
 * The bundled legal texts are DRAFT material and MUST be reviewed/replaced
 * before any public release. Privacy Policy access does not imply consent.
 */
@Composable
fun LegalWelcomeScreen(
  onAccept: () -> Boolean,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val language = LocalePreferences.resolvedLanguage(context)
  val labels = remember(language) { legalLabels() }

  val bundle = remember(context, language) { LegalDocuments.load(context, language).getOrNull() }
  val terms = bundle?.terms
  val privacy = bundle?.privacy

  var selectedTerms by remember { mutableStateOf(true) }
  var checked by remember { mutableStateOf(false) }
  var storageError by remember { mutableStateOf(false) }

  BoxWithConstraints(
    modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)
  ) {
    // Measure before scrolling: a weighted reader can collapse when controls are
    // taller than a landscape window. Give the nested reader a finite height;
    // the outer scroll keeps every control reachable without scrolling a whole document.
    val readerHeight = (maxHeight * 0.45f).coerceIn(160.dp, 420.dp)
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("welcome_page"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Surface(
        modifier = Modifier.fillMaxWidth().testTag("welcome_legal_group"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          LanguagePicker(Modifier.fillMaxWidth())
          Text(labels.title, style = MaterialTheme.typography.headlineSmall)
          Text(labels.explanation, style = MaterialTheme.typography.bodyMedium)

          Text(
            AppStrings.get(R.string.ui_6e7946fdc3),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { selectedTerms = true },
              modifier = Modifier.weight(1f).testTag("welcome_terms")
            ) {
              Text(labels.terms)
            }
            OutlinedButton(
              onClick = { selectedTerms = false },
              modifier = Modifier.weight(1f).testTag("welcome_privacy")
            ) {
              Text(labels.privacy)
            }
          }

          Surface(
            modifier = Modifier.height(readerHeight).fillMaxWidth().testTag("welcome_document_viewport"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
          ) {
            key(selectedTerms, language) {
              Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
              ) {
                SelectionContainer {
                  Text(
                    if (selectedTerms) terms ?: labels.error else privacy ?: labels.error,
                    modifier = Modifier.testTag("welcome_document_text"),
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
              }
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              modifier = Modifier.testTag("welcome_accept_checkbox"),
              checked = checked,
              onCheckedChange = { checked = it; storageError = false },
              enabled = terms != null && privacy != null
            )
            Text(labels.accept, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
          }
          if (storageError) {
            Text(labels.error, color = MaterialTheme.colorScheme.error)
          }
          Button(
            onClick = { if (!onAccept()) storageError = true },
            enabled = checked && terms != null && privacy != null,
            modifier = Modifier.fillMaxWidth().testTag("welcome_continue")
          ) {
            Text(labels.continueLabel)
          }
        }
      }
    }
  }
}
