package com.example.ui.screens

import com.example.ui.viewmodel.NavTab
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveNavigationTest {

  @Test
  fun widthPolicy_usesCompactBelow600Dp() {
    assertEquals(
      NavigationLayoutMode.COMPACT,
      navigationLayoutForWidth(599f)
    )
  }

  @Test
  fun widthPolicy_usesMediumFrom600To839Dp() {
    assertEquals(
      NavigationLayoutMode.MEDIUM,
      navigationLayoutForWidth(600f)
    )
    assertEquals(
      NavigationLayoutMode.MEDIUM,
      navigationLayoutForWidth(839f)
    )
  }

  @Test
  fun widthPolicy_usesExpandedAt840DpAndAbove() {
    assertEquals(
      NavigationLayoutMode.EXPANDED,
      navigationLayoutForWidth(840f)
    )
    assertEquals(
      NavigationLayoutMode.EXPANDED,
      navigationLayoutForWidth(1200f)
    )
  }

  @Test
  fun navigationGroupsCoverEveryPrimaryTabButKeepConnectionInTheHeader() {
    val groupedTabs = nextDnsNavigationGroups.flatMap { it.tabs }

    val primaryTabs = NavTab.values().toSet() - NavTab.ACCOUNT
    assertEquals(primaryTabs, groupedTabs.toSet())
    assertEquals(primaryTabs.size, groupedTabs.size)
    assertEquals(listOf(NavTab.SETTINGS), nextDnsNavigationGroups.last().tabs)
  }
}
