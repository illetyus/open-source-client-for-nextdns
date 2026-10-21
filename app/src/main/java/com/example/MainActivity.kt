package com.example

import android.os.Bundle
import android.content.Context
import com.example.i18n.LocalePreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.preferences.ThemePreferences
import com.example.data.legal.LegalAcceptanceStore
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.LegalWelcomeScreen
import com.example.ui.theme.AppTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.NextDnsViewModel
import com.example.data.local.clearRetiredSpeedTestSettings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocalePreferences.localizedContext(newBase))
  }

  private val viewModel: NextDnsViewModel by viewModels()
  private val legalAcceptanceStore by lazy { LegalAcceptanceStore(applicationContext) }

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    com.example.data.notifications.NotificationCenter.createChannels(this)
    lifecycleScope.launch {
      clearRetiredSpeedTestSettings(applicationContext)
    }
    
    splashScreen.setKeepOnScreenCondition {
        // Keep splash screen on until we know the login status
        legalAcceptanceStore.isAccepted() && viewModel.isInitializing.value
    }
    
    setContent {
        val context = LocalContext.current
        var termsAccepted by remember { mutableStateOf(legalAcceptanceStore.isAccepted()) }
        val themePrefs = remember { ThemePreferences(context) }
        val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

        AppTheme(themeMode = themeMode) {
            val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          if (!termsAccepted) {
            LegalWelcomeScreen(onAccept = {
              val saved = legalAcceptanceStore.acceptCurrentTerms()
              if (saved) {
                termsAccepted = true
                viewModel.resumeAfterTermsAccepted()
              }
              saved
            })
          } else if (isLoggedIn != null) {
            AnimatedContent(
              targetState = isLoggedIn == true,
              transitionSpec = {
                fadeIn(animationSpec = tween(160))
                  .togetherWith(fadeOut(animationSpec = tween(120)))
              },
              label = "auth_navigation"
            ) { loggedIn ->
              if (loggedIn) {
                HomeScreen(viewModel = viewModel)
              } else {
                LoginScreen(viewModel = viewModel)
              }
            }
          }
        }
      }
    }
  }
}
