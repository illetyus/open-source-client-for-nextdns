package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.legal.LegalDocuments

/** Read-only access remains available after the first acceptance; it stores no consent. */
@Composable
fun LegalDocumentLinks(modifier: Modifier = Modifier) {
  var selectedTerms by remember { mutableStateOf<Boolean?>(null) }
  Column(modifier.fillMaxWidth()) {
    TextButton(onClick = { selectedTerms = true }, modifier = Modifier.testTag("legal_terms_link")) {
      Text(stringResource(R.string.legal_terms_label))
    }
    TextButton(onClick = { selectedTerms = false }, modifier = Modifier.testTag("legal_privacy_link")) {
      Text(stringResource(R.string.legal_privacy_label))
    }
    SourceLicenseLink()
  }
  selectedTerms?.let { initial ->
    val context = LocalContext.current
    val bundle = remember(context) { LegalDocuments.load(context).getOrNull() }
    Dialog(onDismissRequest = { selectedTerms = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(if (initial) R.string.legal_terms_label else R.string.legal_privacy_label),
            style = MaterialTheme.typography.headlineSmall)
          if (bundle?.draft == true) Text(stringResource(R.string.legal_draft_warning), color = MaterialTheme.colorScheme.error)
          Surface(Modifier.weight(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
              SelectionContainer {
                Text(if (initial) bundle?.terms ?: stringResource(R.string.legal_document_error)
                  else bundle?.privacy ?: stringResource(R.string.legal_document_error),
                  modifier = Modifier.testTag("legal_document_content"))
              }
            }
          }
          Button(onClick = { selectedTerms = null }, modifier = Modifier.fillMaxWidth().testTag("legal_reader_close")) {
            Text(stringResource(R.string.legal_close_label))
          }
        }
      }
    }
  }
}
