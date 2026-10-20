package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.i18n.AppStrings
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.NextDnsViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val viewModel = NextDnsViewModel()
    composeTestRule.setContent { AppTheme { HomeScreen(viewModel = viewModel) } }

    composeTestRule.onNodeWithText("Open Source Client for NextDNS").assertIsDisplayed()
    composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/branding/greeting.png")
  }

  @Test
  @Config(qualifiers = "w320dp-h800dp-mdpi", sdk = [34])
  fun homeTitleFitsCompactDisplayWithLargeText() {
    val viewModel = NextDnsViewModel()
    composeTestRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
        AppTheme { HomeScreen(viewModel = viewModel) }
      }
    }

    val title = composeTestRule.onNodeWithText(
      "Open Source Client for NextDNS", useUnmergedTree = true
    ).assertIsDisplayed()
    val profile = composeTestRule.onNodeWithText(AppStrings.get(R.string.ui_18d49619ba)).assertIsDisplayed()
    composeTestRule.onNodeWithText(AppStrings.get(R.string.ui_6d063986da)).assertDoesNotExist()
    composeTestRule.onNodeWithTag("home_nextdns_connection_button").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("NextDNS Logo").assertDoesNotExist()

    val layouts = mutableListOf<TextLayoutResult>()
    title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertTrue("The app title must have a measured layout", layouts.isNotEmpty())
    assertFalse("The full app title must fit without clipping", layouts.single().hasVisualOverflow)
    val titleBounds = title.fetchSemanticsNode().boundsInRoot
    assertTrue(titleBounds.bottom <= profile.fetchSemanticsNode().boundsInRoot.top)

    composeTestRule.onRoot().captureRoboImage(
      filePath = "build/outputs/roborazzi/branding/greeting-compact-large-font.png"
    )
  }
}
