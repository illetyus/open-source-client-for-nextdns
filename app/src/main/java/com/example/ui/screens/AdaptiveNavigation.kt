package com.example.ui.screens

import com.example.R
import com.example.i18n.AppStrings

import com.example.ui.viewmodel.NavTab

enum class NavigationLayoutMode {
  COMPACT,
  MEDIUM,
  EXPANDED
}

data class NavigationGroup(
  val titleResource: Int,
  val tabs: List<NavTab>
) {
  val title: String get() = AppStrings.get(titleResource)
}

fun navigationLayoutForWidth(widthDp: Float): NavigationLayoutMode = when {
  widthDp < 600f -> NavigationLayoutMode.COMPACT
  widthDp < 840f -> NavigationLayoutMode.MEDIUM
  else -> NavigationLayoutMode.EXPANDED
}

val nextDnsNavigationGroups: List<NavigationGroup> = listOf(
  NavigationGroup(
    titleResource = R.string.ui_0f1322006d,
    tabs = listOf(NavTab.SETUP, NavTab.ACCOUNT)
  ),
  NavigationGroup(
    titleResource = R.string.ui_110e26ffd4,
    tabs = listOf(NavTab.SECURITY, NavTab.PRIVACY, NavTab.PARENTAL)
  ),
  NavigationGroup(
    titleResource = R.string.ui_9cc4262aec,
    tabs = listOf(NavTab.DENYLIST, NavTab.ALLOWLIST)
  ),
  NavigationGroup(
    titleResource = R.string.ui_1e99298c66,
    tabs = listOf(NavTab.ANALYTICS, NavTab.LOGS)
  ),
  NavigationGroup(
    titleResource = R.string.ui_80ad54ad05,
    tabs = listOf(NavTab.SETTINGS)
  )
)
