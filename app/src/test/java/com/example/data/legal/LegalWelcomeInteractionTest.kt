package com.example.data.legal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.NextDnsApp
import com.example.ui.screens.LegalWelcomeScreen
import com.example.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi", application = NextDnsApp::class)
class LegalWelcomeInteractionTest {
  @get:Rule val rule = createComposeRule()

  @Test fun readingPrivacyDoesNotAcceptTermsOrEnableContinue() {
    var attempts = 0
    rule.setContent { AppTheme { LegalWelcomeScreen(onAccept = { attempts++; true }) } }
    rule.onNodeWithTag("welcome_legal_group").assertExists()
    rule.onNodeWithTag("welcome_accept_checkbox").assertIsOff()
    rule.onNodeWithTag("welcome_continue").assertIsNotEnabled()
    rule.onNodeWithTag("welcome_privacy").performScrollTo().performClick()
    rule.onNodeWithTag("welcome_accept_checkbox").assertIsOff()
    rule.onNodeWithTag("welcome_continue").assertIsNotEnabled()
    rule.runOnIdle { assertEquals(0, attempts) }
  }

  @Test fun failedAcceptanceRemainsOnTheLegalScreen() {
    var attempts = 0
    rule.setContent { AppTheme { LegalWelcomeScreen(onAccept = { attempts++; false }) } }
    rule.onNodeWithTag("welcome_terms").performScrollTo().performClick()
    rule.onNodeWithTag("welcome_accept_checkbox").performScrollTo().performClick()
    rule.onNodeWithTag("welcome_continue").performScrollTo().assertIsEnabled().performClick()
    rule.onNodeWithTag("welcome_accept_checkbox").assertIsOn()
    rule.onNodeWithTag("welcome_continue").assertIsDisplayed()
    rule.runOnIdle { assertEquals(1, attempts) }
  }
}
