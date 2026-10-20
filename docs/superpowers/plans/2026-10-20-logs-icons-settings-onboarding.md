# Logs, Icons, Settings and Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make historical/device logs reachable, load domain favicons safely, and align account, settings, guest, and launcher UI with the approved independent-client design.

**Architecture:** Keep NextDNS API and app technical identifiers unchanged. Add cursor state to the existing repository, use device IDs for server filtering, and expose pagination state to Compose. Load icons only from the fixed first-party NextDNS favicon host with strict domain validation, bounded requests, caching, and a local fallback. Group service data, legal access, and local preferences by source and task.

**Tech Stack:** Kotlin, Jetpack Compose, Retrofit, OkHttp, Android resources, Robolectric/Compose tests, Gradle.

**Spec:** `docs/UX_LOGS_ICON_DESIGN_RESEARCH_DRAFT_2026-10-20.md`

## Global Constraints

- Preserve `api.nextdns.io`, the Android application ID, Kotlin/package identifiers, and protocol contracts.
- Keep the client independent and unofficial; never imply a NextDNS partnership.
- Keep legal documents marked `DRAFT`; do not invent publisher identity or contact details.
- Do not send application-local appearance, language, or notification preferences to NextDNS.
- Do not transmit queried domains to third-party icon or analytics services.
- Keep the in-app logo unchanged; redesign only launcher resources.
- The favicon route is observed first-party behavior, not a published API guarantee; requests must be bounded and fall back locally.

## Review Focus

- Cursor/filter state from a previous profile or selected device must never populate the new query; cover profile and filter changes in repository tests.
- Missing/invalid favicon host input must never create a request; cover valid IDN normalization and malformed domains.
- A stream event arriving during a page request must not be lost or duplicated; cover overlapping refresh/page data.
- Legal acceptance remains explicit, and read-only document access must not change consent; retain existing acceptance tests.
- Large fonts, long localized labels, and compact windows must keep guest controls and section actions reachable; retain viewport and Compose tests.

---

### Task 1: Device-aware log pagination

**Files:**
- Modify: `app/src/main/java/com/example/data/api/NextDnsApiService.kt`
- Modify: `app/src/main/java/com/example/data/model/NextDnsModels.kt`
- Modify: `app/src/main/java/com/example/data/repository/NextDnsRepository.kt`
- Modify: `app/src/main/java/com/example/ui/viewmodel/NextDnsViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/LogsScreen.kt`
- Modify: five localized `strings.xml` files
- Test: `app/src/test/java/com/example/data/api/NextDnsApiContractTest.kt`, repository regression/audit tests, and Compose log tests

**Interfaces:**
- Produce `LogsPaginationState(hasMore: Boolean, isLoading: Boolean, error: String?)` as a repository/ViewModel state flow.
- Produce `suspend fun loadMoreLogsFromApi(limit: Int = 100): Boolean` and `suspend fun setLogsDeviceFilter(deviceId: String?): Boolean`.
- Preserve `refreshLogsFromApi(limit: Int = 100)` call compatibility; it refreshes the current device filter and resets cursor history.
- Parse and preserve the API device ID on each `DnsLogEntry`; use `__UNIDENTIFIED__` for the API filter sentinel.

- [x] Add a repository test proving page 2 sends the returned cursor and appends older unique records.
- [x] Add a repository test proving selected device ID and `__UNIDENTIFIED__` are sent to `/logs`, and profile/filter changes reject stale pages.
- [x] Run focused tests and confirm they fail on missing pagination/filter behavior.
- [x] Implement cursor state, reset it on refresh/profile/logout/log deletion, and merge stream events without duplicate page rows.
- [x] Add an explicit localized “Load older logs” action, loading/error states, and device options keyed by API IDs; stop scrolling to the top when older rows append.
- [x] Run the focused repository, API, and Compose tests.

### Task 2: First-party favicon loading with privacy fallback

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/FaviconImage.kt`
- Modify/create: focused favicon URL/loading helper under `app/src/main/java/com/example/ui/`
- Test: focused favicon helper/Compose tests

**Interfaces:**
- Produce a pure URL builder that returns a URL only for a valid DNS hostname and always uses `https://favicons.nextdns.io/`.
- Keep `FaviconImage(domain, modifier)` source-compatible and retain the Material fallback.

- [x] Add tests for ASCII/IDN hostname encoding and invalid URLs, paths, ports, wildcard strings, and empty input.
- [x] Run focused tests and confirm the missing loader/validation assertion fails.
- [x] Load remote bytes on IO with redirect following disabled, bounded timeouts/response size, and a memory cache; use the generic icon for failures.
- [x] Run favicon tests and verify the in-app logo resource is not touched.

### Task 3: Source-based settings and connection navigation

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/AccountScreen.kt` as needed
- Modify: `app/src/main/java/com/example/ui/viewmodel/NextDnsViewModel.kt` only for relocated actions
- Modify: five localized `strings.xml` files
- Test: `app/src/test/java/com/example/audit/AccountSettingsFlowTest.kt`

**Interfaces:**
- Keep `NavTab.ACCOUNT` as an internal enum identifier while presenting it as the NextDNS connection/account data screen.
- Keep diagnostic, refresh/sync, and logout reachable from the connection screen after removing the top account dropdown.

- [x] Add order and navigation tests: service settings first, the app-local preferences group at the end with legal access after its controls, and destructive profile deletion as the final action; assert the home account dropdown is absent and connection actions remain accessible.
- [x] Add a request-recording assertion that a full refresh performs reads only and never sends local preferences in a mutation.
- [x] Run focused tests and confirm current order/menu behavior fails.
- [x] Put NextDNS service settings first; place app-local controls and the legal/about group at the end, with legal documents immediately before profile deletion; rename visible connection text in all five locales; remove the duplicate account menu.
- [x] Run account/settings Compose and sync regression tests.

### Task 4: Consolidated guest and pre-login legal presentation

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/LegalWelcomeScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/LoginScreen.kt`
- Modify: five localized `strings.xml` files if new copy is required
- Test: `app/src/test/java/com/example/data/legal/LegalWelcomeInteractionTest.kt`, `LegalWelcomeViewportTest.kt`, and new pre-login composition tests

**Interfaces:**
- Keep the current explicit terms acceptance callback and read-only terms/privacy reader behavior.
- Use system language by default and keep the existing app-language selector.

- [x] Add a Compose test for one grouped language/legal acceptance surface and no repeated legal links on the login/guest screen.
- [x] Run the focused tests and confirm the duplicated-link/layout assertion fails.
- [x] Group the language control, DRAFT note, terms/privacy tabs, reader, acceptance, and continue action; remove duplicate login legal links without bypassing the acceptance gate.
- [x] Run legal acceptance and viewport tests in compact portrait/landscape configurations.

### Task 5: Minimal white adaptive launcher icon and full verification

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: legacy launcher mipmap assets if still selected below API 26
- Test: launcher resource verification plus relevant Compose/repository suites

**Interfaces:**
- Keep manifest references and all in-app logo resources unchanged.

- [x] Add resource assertions for a white adaptive background, non-empty foreground/monochrome layers, and unchanged in-app logo.
- [x] Run resource tests and confirm they fail against the current navy background/large mark.
- [x] Replace only launcher layers/legacy variants with a minimal centered independent DNS-node mark inside the adaptive safe area.
- [x] Run the complete unit/UI suite, debug/release packaging, Android Lint and dependency/package checks; record Snyk, BrowserStack, and live-device checks that could not run; review the diff and update the defect ledger.
