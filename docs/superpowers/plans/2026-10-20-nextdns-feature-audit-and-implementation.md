# NextDNS API, Performance, Logs and Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Audit the existing independent NextDNS Android client against the ten requested API, diagnostics, performance, logs, analytics, and icon requirements; implement only behavior supported by a documented or observed contract and validate it through tests and GitHub CI.

**Architecture:** Keep the current Kotlin/Compose application and its `NextDnsApiService`/repository/ViewModel boundaries. Separate official NextDNS API capabilities from the diagnostic endpoints and app-local features. Add UI only for data the server actually returns; preserve unknown fields without inventing their meaning, and defer writes until the API contract is established. Measure performance with AndroidX tracing and Macrobenchmark on a named device/runtime, and keep any speed test explicitly user-started, cancellable, transparent about its destination, and free of telemetry.

**Tech Stack:** Kotlin, Jetpack Compose, Retrofit/OkHttp/Moshi, coroutines, Robolectric/Compose tests, AndroidX Macrobenchmark/JankStats, Gradle, GitHub Actions.

**Spec:** `docs/UX_LOGS_ICON_DESIGN_RESEARCH_DRAFT_2026-10-20.md` and the user-provided `pasted-text-1.txt` requirements copied into this plan.

## Global Constraints

- Verify API fields and endpoints against the current NextDNS API documentation or a sanitized real response; do not guess names or behavior.
- Preserve the independent/unofficial product position, `applicationId`, API host, Kotlin package/class names, and existing protocol contracts.
- Do not send application-local language, theme, notification, or connection-state preferences to NextDNS.
- Keep secrets, API keys, query names, and private profile data out of logs, commits, test output, and research queries.
- Keep all user-facing strings localized in Turkish, English, German, French, and Spanish.
- Keep legal documents in `DRAFT` status; do not invent publisher identity, contact information, or publication approval.
- Never infer a city or region from a resolver hostname, IP, or Anycast flag. Show the exact server identifier and an explicit unavailable label when location is not verifiable.
- Any speed test must be explicitly initiated, cancellable, HTTPS-only, endpoint-disclosed, non-telemetric, and covered by verified software and server terms before release.
- Treat `favicons.nextdns.io` as an observed best-effort service, not a published NextDNS API guarantee; retain host validation, bounded transfer, and a local fallback.

## Review Focus

- Unknown security fields must not silently become editable unless their type and write contract are established; test unknown strings, numbers, objects, and booleans separately.
- Log filter, device, date, profile, and cursor changes must invalidate stale pages; test a late response racing a newly selected filter.
- DNS server identifiers and Anycast must not be presented as a verified city; test null, malformed, and present diagnostic fields.
- Speed-test cancellation, metered networks, upload confirmation, and privacy disclosure must not leave background transfers running.
- GAFAM percentages, query counts, colors, and labels must describe the same six segments and remain readable in light/dark themes and accessibility services.

## Requirement-to-code and test matrix

| Req. | Priority | Primary code paths | Verification path | Starting status |
|---|---|---|---|---|
| 1. Age-verification option | P1 research gate | `app/src/main/java/com/example/data/api/NextDnsApiService.kt`, `app/src/main/java/com/example/ui/screens/SecurityScreen.kt` | `app/src/test/java/com/example/data/api/NextDnsApiContractTest.kt`; official schema and sanitized response evidence | No exact field or read/write contract in published schema; no speculative UI |
| 2. Compose/UI performance | P1 | `app/build.gradle.kts`, `settings.gradle.kts`, new `benchmark/` module, `.github/workflows/ci.yml` | AndroidX Macrobenchmark, Compose trace, StrictMode instrumentation; same-device before/after report under `docs/performance/` | No benchmark module or physical-device baseline |
| 3. DNS server/location | P1 | `NextDnsApiService.kt`, `app/src/main/java/com/example/data/model/NextDnsModels.kt`, `app/src/main/java/com/example/data/repository/NextDnsRepository.kt`, `app/src/main/java/com/example/ui/screens/AccountScreen.kt`, localized `strings.xml` | `app/src/test/java/com/example/data/api/NextDnsApiContractTest.kt`, repository and diagnostic Compose tests | `server`, `resolver`, `anycast` DTO fields exist; UI mapping/fallback must be checked |
| 4. Open-source speed test | P1 privacy/security gate | new `app/src/main/java/com/example/data/diagnostics/SpeedTestEngine.kt`, `app/src/main/java/com/example/ui/screens/SpeedTestScreen.kt`, `app/src/main/res/values*/strings.xml`, `app/src/main/assets/licenses/`, `app/build.gradle.kts` | new deterministic speed-test engine and screen tests; HTTPS endpoint and license/NOTICE audit | No engine or disclosed destination; candidates have different licensing/server requirements |
| 5. Device-name fallback | P2 | `NextDnsModels.kt`, `NextDnsRepository.kt`, `AnalyticsScreen.kt`, `LogsScreen.kt` | new device-label unit tests and `UiAuditTest.kt` | Placeholder-name risk; API IDs must remain separate from display labels |
| 6. Logs categories/reasons/history | P1 | `NextDnsApiService.kt`, `NextDnsModels.kt`, `NextDnsRepository.kt`, `NextDnsViewModel.kt`, `LogsScreen.kt`, localized `strings.xml` | `NextDnsApiContractTest.kt`, `RepositoryRegressionTest.kt`, `UiAuditTest.kt` | Cursor pagination/device filtering exist in local diff; status UI and exact reason coverage are not yet proven |
| 7. Early-access security | P1 | `NextDnsApiService.kt`, `NextDnsRepository.kt`, `ConfigSnapshotHasher.kt`, `SecurityScreen.kt` | API parser, PATCH/read-back, snapshot, and UI tests | Known fields are typed; unknown response keys are currently discarded |
| 8. GAFAM chart | P2 | new `app/src/main/java/com/example/ui/screens/GafamChartModel.kt`, `AnalyticsScreen.kt` | new `app/src/test/java/com/example/ui/screens/GafamChartModelTest.kt` and analytics Compose/screenshot regression | Legend has six groups; canvas omits Apple and Amazon slices |
| 9. API inventory | P1 | new `docs/NEXTDNS_API_CAPABILITY_MATRIX_TR.md`, `docs/PROJECT_PHASE_STATUS.md`, API contract tests | annotation-to-matrix manual audit and focused Gradle tests | Endpoint knowledge is split across service/repository/screens; no single matrix |
| 10. Favicon quality/cache | P2 privacy gate | `FaviconImage.kt`, `NextDnsFaviconUrlTest.kt`, `app/src/main/assets/licenses/THIRD_PARTY_NOTICES.txt`, privacy draft and provenance docs | mocked HTTPS URL/loader/cache tests and privacy/license review | Bounded best-effort NextDNS host and in-memory cache exist; route is unofficial |

## Interface boundaries

- `NextDnsApiService` remains the HTTP boundary. Repository methods translate its DTOs into app models; Compose screens consume repository/ViewModel state only.
- `DeviceLabelResolver.label(name, model, unidentified)` will be the shared display-label boundary for logs and analytics; stable IDs stay in the existing filter state.
- `GafamSegmentModel.from(analytics)` will be a pure calculation returning the same ordered segment values used by both the donut and legend; chart drawing will not recompute separate totals.
- `SpeedTestEngine` will expose a cancellable suspend operation over an explicitly selected `SpeedTestServer`; UI disclosure and network execution share that selected server object.
- `ApiCapabilityMatrix` is documentation, not runtime configuration. It cannot authorize a write; runtime mutation support still requires a contract test.

## Explicit uncertainty constraints

```xml
<design_and_scope_constraints>
Ensure all features are supported by verified API responses or documented diagnostics; avoid assumptions or speculative features. Scope changes only to supported or verifiable API options.
</design_and_scope_constraints>

<uncertainty_and_ambiguity>
Explicitly mark any assumptions, uncertainties, or undocumented features; suggest research steps to confirm API support or behavior.
</uncertainty_and_ambiguity>
```

## Current evidence and repository state

- GitHub repository: `illetyus/open-source-client-for-nextdns`; public, writable by the connected account; default branch `main`.
- GitHub `main` was fetched and verified at `c7db8d9d04d2022741ccc1564877940f45fe485a`. Work is isolated in `feat/nextdns-api-performance-audit`; draft PR #27 was opened from that exact main commit for a test-first red phase. Its initial regression changes and implementation are tracked in the PR; CI outcomes are recorded below.
- The existing local plan `docs/superpowers/plans/2026-10-20-logs-icons-settings-onboarding.md` records log cursor pagination/device filtering, first-party best-effort favicon loading, settings/guest/legal grouping, and launcher redesign as locally implemented. Its recorded build and test results predate this plan and do not prove the present tree or GitHub CI.
- Official NextDNS API documentation currently labels the API beta, documents cursor/limit pagination, security fields, profile settings, and log parameters. It does not document an age-verification bypass field. The codebase has no live account API-key evidence available to this audit.
- A NextDNS Help Center bug report dated 2026-03-09 discusses the option's dashboard availability but provides no API field or read/write contract. This is community evidence about uncertainty, not proof that the feature is absent.
- `NextDnsTestResponse` models `server`, `resolver`, and `anycast`; these are shown without geo inference. The response does not provide a region field, so the screen now has a five-language explicit unavailable-region row.
- At the verified live-main base, logs already use documented status/device/search parameters, cursor pagination, stale-response rejection and raw-reason fallback; date/sort controls remain a documented UI gap.
- At the verified live-main base, the interactive country map and six-segment GAFAM chart already exist. They will be validated through PR CI rather than reimplemented.
- `FaviconImage` uses HTTPS `favicons.nextdns.io`, bounded network reads, and a generic fallback. The route is not in the published API reference and the cache is process-memory only.
- No speed-test or Macrobenchmark module is present. The local ADB server currently fails to start, so a live-device measurement is not yet available.

### Execution update — 20 October 2026

- PR #27 red-phase runs `39410235278`, `39411101417`, and `39411876774` passed lint, metadata, localization, and legal-draft checks. Their targeted assertions failed on the missing diagnostic region row and 16 dp list icons; the TLD test first exposed Compose/Espresso cursor-animation timing. Run `39412587641` then failed all three targeted assertions for the expected old behavior, including the missing unavailable-catalog label, across 194 tests.
- CI `39414065197` (`ff533ecef89e0105cb6f3eccd61c34688846b05b`) passed Android Lint, metadata, five-language and legal-draft gates; 193 of 194 tests passed. The only failure was the TLD dialog regression assertion. The UI test had frozen Compose time before triggering the dialog, and the UI treated the initial empty list as unavailable. The implementation now distinguishes loading, available, empty, and unavailable; the test advances one frame after the click and verifies the loading state and absence of guessed TLDs. These changes are awaiting a new CI run.
- Local Gradle tests could not start because the configured wrapper-cache lock returned access denied. No local build, lint, unit test or APK result is claimed; GitHub CI remains the executable validation path.
- BrowserStack credentials are not configured in the current process or Windows user/machine environment, and no device is attached. Physical-device performance values, recomposition traces and BrowserStack results therefore remain unverified.
- The speed-test service choice is still awaiting the user. No public M-Lab endpoint or associated AUP/privacy obligations will be accepted by default; a configurable owned LibreSpeed endpoint remains the recommended direction.
- Keep age-verification and early-access settings unsupported until a documented API field or sanitized response proves them; do not guess names or send speculative writes. The current API matrix and error ledger record these boundaries.

---

## Numbered work items

### 1. Age-verification bypass: verify API existence and gate implementation

**Current status:** Published API schema and NextDNS Help Center were reviewed on 20 October 2026. The service feature appears in Help Center discussion, but no API field or read/write contract is documented; no sanitized account response was available. It remains `UNVERIFIED / NOT IMPLEMENTED`.

**Root Cause Analysis:** The dashboard toggle is absent from the public API schema. A setting name seen in third-party scripts or guessed from UI copy would not prove read or write support.

**Scope of Change:** Record the exact status, endpoint, field name, read/write support, and potential UI mapping. No mutation is added while the field remains undocumented and unobserved.

**Implementation Steps:**
- [x] Compare the published profile/security/settings schemas and public NextDNS help discussion; retain source URLs and access date in the API matrix.
- [ ] Check sanitized, user-authorized API response evidence only if such evidence becomes available; never request or print an API key.
- [ ] If a real response exposes a candidate field, add a parser regression first, then test a read-only display. Add a toggle only after a documented or verified non-destructive PATCH/read-back contract exists.
- [x] Otherwise mark the field `UNDOCUMENTED / NOT IMPLEMENTED`, explain how an owner can verify it locally, and keep it out of synchronization and mutation payloads.

**Testing Strategy:** Run the existing `NextDnsApiContractTest` suite to confirm documented security/settings requests remain unchanged. This research-only task adds no runtime field or serializer; a later API-driven parser is tested under Task 7, including unknown JSON values and PATCH/read-back behavior.

**Acceptance Criteria:** The report states the exact field name as `not documented` unless evidence proves otherwise; no speculative request is issued; normal settings writes are unchanged.

**Backward Compatibility Considerations:** Existing profile/settings responses and mutation DTOs remain valid when the field is absent.

**Performance & Security Considerations:** Do not collect credentials or personal profile data for research; unknown values remain inert until typed and verified.

**Design System & UI Adjustments:** No switch is shown for an unsupported option. If verified later, the control is placed with the corresponding NextDNS service settings, not application-local preferences.

**Phased PR Plan:** Include the evidence row and documentation in PR 1; a later verified implementation gets a separate PR with a redacted response fixture.

### 2. Performance and UI-fluidity measurements

**Current status:** The world-map asset read was moved off the main thread and loading/error UI was added. StrictMode auditing, Macrobenchmark instrumentation, Perfetto traces, recomposition measurements, and same-device baseline/candidate results remain incomplete.

**Root Cause Analysis:** The repository has no repeatable performance baseline for Compose recompositions, UI frame timing/jank, startup, or main-thread I/O. Source inspection and passing UI tests do not quantify these properties.

**Scope of Change:** Add a reproducible measurement harness and report before/after values for startup and representative home, logs, and analytics interactions on a named API/device configuration.

**Implementation Steps:**
- [ ] Add a test-only StrictMode audit for disk/network work on the main thread and document known false positives.
- [ ] Add AndroidX Macrobenchmark cases using `StartupTimingMetric` and `FrameTimingMetric`; collect release-like builds and Perfetto traces.
- [ ] Enable Compose runtime tracing only in the profiling configuration and record recomposition/layout observations for the same named scenarios.
- [ ] Capture baseline from the current `main` commit and candidate from the feature branch on the same device/runtime; store raw benchmark output and a concise comparison report.
- [ ] If local device access remains unavailable, run supported CI/BrowserStack checks and mark any emulator/cloud timing as non-equivalent to a physical-device baseline.

**Testing Strategy:** Benchmark configuration smoke test; Macrobenchmark startup and scroll/navigation scenarios; StrictMode test fails on intentional main-thread network/disk fixtures; repeat runs to report median and percentile rather than a single result.

**Acceptance Criteria:** A versioned report identifies commit, device, Android version, build type, iterations, startup and frame timing/jank values, trace locations, main-thread I/O outcome, and recomposition inspection method. No unsupported “smoothness” claim is made.

**Backward Compatibility Considerations:** Profiling dependencies and instrumentation do not affect release behavior or the minimum supported Android version.

**Performance & Security Considerations:** Avoid production logging of query contents or API responses; benchmark only synthetic data and release-like builds.

**Design System & UI Adjustments:** Keep current Compose layouts; change UI only where measurements and a reproducible user-facing defect justify it.

**Phased PR Plan:** PR 2 adds harness and baseline procedure; PR 3 records measurements and narrowly scoped performance fixes.

### 3. DNS resolver/server identity and location wording

**Current status:** Implemented in source and covered by Compose tests: the screen shows only the returned server/resolver/Anycast values, identifies `test.nextdns.io` and the local check time, and uses an explicit unavailable label. CI/runtime verification remains pending.

**Root Cause Analysis:** The diagnostic response can provide server/resolver identifiers and an Anycast flag, but these values do not by themselves prove a city or country.

**Scope of Change:** Surface only diagnostic fields actually returned by `test.nextdns.io`; show “Bilgi alınamıyor” (localized equivalents) for a missing or unverified geographic location.

**Implementation Steps:**
- [x] Trace the response model through repository, `DiagnosticTestResult`, and connection UI.
- [x] Preserve the raw server identifier and Anycast value with a visible source/time; do not reverse-map suffixes or IPs to cities.
- [x] Add explicit unavailable copy for absent location data and document the separate endpoint in the API capability matrix.

**Testing Strategy:** Fixtures for `server` present/absent, `anycast` true/false/null, resolver present/absent, malformed payload, and stale profile response.

**Acceptance Criteria:** The UI never labels a city without a verified mapping; it displays the server ID as returned and the localized unavailable text otherwise.

**Backward Compatibility Considerations:** All diagnostic fields are nullable; older or partial responses continue to render.

**Performance & Security Considerations:** Do not send the client IP/resolver data to an external geolocation service.

**Design System & UI Adjustments:** Add a compact diagnostic detail row in the existing connection/account diagnostic context with an accessibility label.

**Phased PR Plan:** PR 1 with diagnostic data mapping and regression tests.

### 4. User-controlled open-source internet speed test

**Current status:** Client/license candidates were compared. No acceptable, verified HTTPS test server and usage terms are available, so runtime transfer code and UI remain deferred.

**Root Cause Analysis:** No speed-test engine exists. A library license alone does not grant permission to use a third-party test server, and the candidate JSpeedTest project documents legacy HTTP/FTP endpoints as examples.

**Scope of Change:** Select an Android-compatible open-source engine only after verifying license, maintenance/dependency risks, HTTPS support, ping/upload behavior, and an actual server’s terms. Do not silently use a vendor’s undocumented endpoint.

**Implementation Steps:**
- [x] Compare the MIT JSpeedTest client with the LGPL-3.0 LibreSpeed Android template; inspect source, dependency freshness, TLS behavior, server configuration, and NOTICE obligations.
- [ ] Verify a test endpoint/server is currently online over HTTPS and permits user-initiated test traffic; otherwise make the server configurable or record the item as blocked pending a valid server.
- [ ] Implement a foreground, user-started flow with server identity, approximate transfer size, upload confirmation, metered-network notice, progress, cancellation, and explicit results deletion/no-retention behavior.
- [ ] Keep test traffic, IP information, and results out of analytics and logs; add license/NOTICE and privacy copy in all five languages.

**Testing Strategy:** Deterministic local HTTP(S) test server for ping, download, upload, rate calculation, timeout, TLS failure, cancellation, and metered-network prompts; license inventory and release dependency scan.

**Acceptance Criteria:** Download/upload Mbps and ping are measured against the disclosed HTTPS server; cancellation closes sockets promptly; no transfer starts before user action; no telemetry or result persistence occurs unless separately enabled and explained.

**Backward Compatibility Considerations:** New feature is additive, does not change NextDNS DNS transport, API credentials, or default app startup network traffic.

**Performance & Security Considerations:** Bound duration and bytes, cap concurrent transfers, require TLS, reject redirects to unapproved hosts, and do not run in background.

**Design System & UI Adjustments:** Put it under explicit diagnostics/tools; show destination, data-use estimate, and separate download/upload controls in accessible localized UI.

**Phased PR Plan:** PR 4 only after engine, endpoint, licensing, and privacy review pass; otherwise publish the verified blocker and do not ship a misleading button.

### 5. Device analysis name fallback

**Current status:** Implemented in repository, analytics, and logs source with API-ID filtering; unit and Compose regressions are present. CI verification remains pending.

**Root Cause Analysis:** API analytics can omit device name/model or return placeholder text; rendering a nullable field directly can produce “undefined” or an empty label.

**Scope of Change:** Prefer the API device name, then the API model where suitable; when neither is valid, render the exact localized equivalent of “Tanımlanamayan cihaz.” Keep API device ID for filters, not as a human-readable name.

**Implementation Steps:**
- [x] Centralize device-label normalization and use it in analysis and logs.
- [x] Reject blank strings and literal placeholders such as `undefined`/`null` without changing the server ID.
- [x] Ensure filter actions still use stable API IDs.

**Testing Strategy:** Unit fixtures for name, model-only, blank, placeholder, unidentified sentinel, and ID-only cases; Compose assertion for the Turkish fallback.

**Acceptance Criteria:** No visible “undefined”, blank device name, or raw identifier appears as a user-facing device label; API filtering remains ID-based.

**Backward Compatibility Considerations:** Existing valid server names render unchanged.

**Performance & Security Considerations:** Do not infer a person's name or device from IP/profile metadata.

**Design System & UI Adjustments:** Use one shared device-label function for analytics and logs to prevent inconsistent copy.

**Phased PR Plan:** PR 1 with log/device accuracy fixes.

### 6. DNS logs: status categories, reasons, and history

**Current status:** API status/device/search filtering, cursor resets, stale-page rejection, exact unknown-reason fallback, and pagination/stream integration are implemented with repository/UI regressions. Date and sort controls remain absent from the screen; existing retention limits are documented. Tests have not been executed in this environment.

**Root Cause Analysis:** The API supports `status`, `device`, date, search, `raw`, `limit`, and `cursor`; first-page-only behavior or client-only filtering can hide historical/device-specific records and misstate why an event was blocked.

**Scope of Change:** Preserve already implemented cursor pagination and API-ID device filters; add any missing blocked/allowed/default/error filters and render actual API reasons without inventing rule explanations.

**Implementation Steps:**
- [x] Compare `NextDnsApiService`, repository query state, and `LogsScreen` controls with the official logs contract.
- [x] Keep the exposed filter tuple (profile, device, status, search, raw) bound to cursor state; reset and reject stale pages on each exposed filter change. Date/sort controls remain unexposed.
- [x] Map returned reason IDs/names faithfully and preserve the raw ID as the fallback for unknown reason values.
- [x] Preserve stream/page deduplication and document the API's retention/history limits in the UI-facing capability record.

**Testing Strategy:** Contract tests for each query parameter and API status; repository tests for filter reset, cursor continuation, stale response rejection, streaming overlap, duplicate rows, and unknown reasons; Compose tests for category chips and empty/error states.

**Acceptance Criteria:** Blocked/allowed filters call the documented `status` parameter; pagination reaches the last returned cursor; devices use API IDs; reasons reflect response data; the app never claims history older than the API retention window.

**Backward Compatibility Considerations:** Default log query and stream behavior stay unchanged when no filters are selected.

**Performance & Security Considerations:** Bound page size and in-memory retention; never log query domains, client IPs, or raw API bodies.

**Design System & UI Adjustments:** Accessible labeled filter chips, stable event hierarchy, and clear pagination loading/error feedback in all supported locales.

**Phased PR Plan:** PR 1 extends the local pagination implementation and closes the device-log findings.

### 7. Server-driven early-access security settings

**Current status:** Deferred at the evidence gate. No sanitized user-authorized response demonstrates an unknown field, so no dynamic label, control, or PATCH behavior is invented. The capability matrix records the limitation.

**Root Cause Analysis:** The fixed `SecurityDto` enumerates published fields, so Moshi discards unknown response keys. Hardcoding guessed names would be unsafe, but discarding server-provided fields prevents future capability discovery.

**Scope of Change:** Preserve unknown JSON values from the documented security response. Expose only safely typed, explicitly identified server-returned values; keep unsupported types read-only/diagnostic. Unknown write support remains conditional on endpoint verification.

**Implementation Steps:**
- [ ] Add a narrowly scoped Moshi adapter or raw security JSON model that captures unknown keys without changing known typed fields.
- [ ] Add a server-field presentation model that distinguishes documented fields from unknown fields and avoids fabricated labels/descriptions.
- [ ] For an unknown Boolean field, permit a write only if the existing security PATCH endpoint demonstrably accepts a single-field patch and the repository verifies it with a subsequent GET; otherwise show it read-only.
- [ ] Include dynamic fields in sync snapshots and error handling without allowing app-local values to enter the API payload.

**Testing Strategy:** Moshi fixture tests for known keys, unknown Boolean/string/number/object/list/null; PATCH serialization tests; mocked acceptance/rejection/read-back behavior; snapshot hash tests.

**Acceptance Criteria:** Unknown response fields are not silently lost; no setting name is invented; known behavior is unchanged; a UI mutation is offered only for an observed Boolean field with a passing PATCH/read-back contract.

**Backward Compatibility Considerations:** Older profiles missing new fields retain current defaults and controls.

**Performance & Security Considerations:** Cap payload size and unknown-field count; do not render values containing sensitive data verbatim; do not persist API responses beyond existing cache policy.

**Design System & UI Adjustments:** Put server-discovered security controls in the service security section, label unrecognized fields neutrally, and provide a non-editable state when writable semantics are unverified.

**Phased PR Plan:** PR 3 adds parsing/read-only discovery; write support follows in a separate PR only with contract evidence.

### 8. GAFAM analytics chart correctness and accessibility

**Current status:** Six-segment count-based model, accessibility description, API slug normalization, and light/dark colors are implemented with unit and Compose regressions. CI verification remains pending.

**Root Cause Analysis:** The current legend includes Google, Facebook, Microsoft, Apple, Amazon, and Other, but the donut renderer draws only the first three companies plus Other. Apple and Amazon query counts are excluded from the plotted arcs, so visual segments and labels disagree.

**Scope of Change:** Derive one canonical six-segment model and use it for both donut arcs and legend; define stable high-contrast colors and accessible text descriptions for light/dark themes.

**Implementation Steps:**
- [x] Extract a pure GAFAM segment calculation from `AnalyticsScreen.kt` using actual query counts and total-query denominator.
- [x] Normalize missing/over-total data safely; derive Other from the remaining total without negative or >100% sweeps.
- [x] Render all six segments in stable order and use the same `GafamSegment` values for labels, counts, colors, and chart semantics.
- [x] Increase color swatches to a visible size and add Compose semantics describing each group and share.

**Testing Strategy:** Unit tests for all five companies, missing companies, zero total, over-total counts, and sum-to-100 behavior; Compose tests proving every legend entry has a matching segment/color and remains accessible in light/dark mode.

**Acceptance Criteria:** Every non-zero company group has a visible arc; plotted totals match legend query counts/percentages; unknown or missing companies do not break rendering; TalkBack has an equivalent text description.

**Backward Compatibility Considerations:** Analytics API requests and `AnalyticsSummary` payload shape stay unchanged.

**Performance & Security Considerations:** Keep chart drawing O(number of fixed segments); do not retain analytics outside existing repository state.

**Design System & UI Adjustments:** Stable palette, contrast-aware dark variants, visible legend markers, and no reliance on color alone.

**Phased PR Plan:** PR 1; include screenshot/regression evidence.

### 9. Comprehensive API capability inventory

**Current status:** `docs/NEXTDNS_API_CAPABILITY_MATRIX_TR.md` maps published endpoints to this client, distinguishes local/diagnostic/undocumented data, and records the API beta caveat. Repository invariants already have regression tests. Scripted consistency execution and Gradle verification remain pending.

**Root Cause Analysis:** Existing API calls are distributed across Retrofit declarations, repository methods, and screens; there is no single audited mapping of supported/read-only/writable/missing/unsupported options.

**Scope of Change:** Produce a versioned matrix for profile/setup, security, privacy, parental control, allow/deny lists, settings, logs, analytics, diagnostics, and app-local data.

**Implementation Steps:**
- [x] Enumerate current official API endpoints, methods, query parameters, response fields, and cursor semantics from `https://nextdns.github.io/api/`.
- [x] Cross-map each field to `NextDnsApiService`, DTOs, repository reads/writes, UI, and tests.
- [x] Classify each row `SUPPORTED_READ_WRITE`, `SUPPORTED_READ_ONLY`, `PARTIAL`, `MISSING`, or `UNVERIFIED`; include evidence link and limitations.
- [x] Add and retain repository/UI regression tests for critical endpoint/request invariants, including absence of `/account` and no mutation of app-local preferences.

**Testing Strategy:** Scripted inventory consistency check against Retrofit annotations and selected API contract tests; manual review of every row against the official documentation.

**Acceptance Criteria:** Every known API surface and user-visible section appears once; account fields are not presented as server API options; unsupported and undocumented items are explicit.

**Backward Compatibility Considerations:** Documentation and audit tests do not change runtime behavior.

**Performance & Security Considerations:** Use no production credentials or live personal profile data in fixtures; redact sample identifiers.

**Design System & UI Adjustments:** Mark controls with data source labels where needed (NextDNS service vs app-local) to prevent false synchronization expectations.

**Phased PR Plan:** PR 1 adds the inventory; later PRs update its statuses as features are verified.

### 10. NextDNS favicon quality, cache, and provenance

**Current status:** Synthetic HTTPS GET confirmed 200 `image/png` for github.com/google.com and 404 for example.com on 20 October 2026. The observed route remains best-effort; redirect/MIME/size/timeout behavior is bounded in code, including a shared eight-second deadline. DNS log rows use local icons so browsing-history domains are not sent to the favicon host. Full network-path tests and a provider guarantee are unavailable. Memory-only cache and local fallback are retained.

**Root Cause Analysis:** Current code uses a high-resolution route on `favicons.nextdns.io`, but the route is absent from the published API contract and the cache is memory-only; provider response quality and reuse terms are not guaranteed.

**Scope of Change:** Retain the host only as best-effort after confirming observed route/HTTPS behavior; add a bounded disk cache only if storage lifecycle and licensing/privacy disclosures are acceptable; otherwise keep the memory cache and local fallback.

**Implementation Steps:**
- [x] Verify live HTTPS response behavior for representative synthetic hostnames and document that the route is unofficial/unsupported by the public API specification.
- [x] Review NextDNS Help Center guidance and repository/community usage evidence; do not treat third-party code as an API guarantee.
- [ ] Test redirects, MIME mismatch, oversized bodies, timeouts, malformed images, IDN inputs, local/private hostnames, and cancellation.
- [x] Evaluate disk caching against user data deletion, expiry, size cap, and privacy documentation before implementation; retain memory-only cache and local fallback.
- [x] Record source/provenance and attribution requirements for locally bundled visual assets.
- [x] Keep browsing-history domains off the remote favicon request path; use a local icon in DNS log rows and state this boundary in the DRAFT privacy notice.
- [x] Apply an OkHttp 4.10 whole-call deadline to DNS resolution, connection, headers, and response-body reads; configure redirects and connection retries off.

**Testing Strategy:** Pure URL validation, mocked HTTPS transport, bitmap bounds, redirect refusal, cache hit/eviction/expiry, and local fallback Compose tests.

**Acceptance Criteria:** No non-HTTPS or third-party favicon request; strict byte/dimension/time limits; failed service always shows a local icon; no claim that the endpoint is an official API.

**Backward Compatibility Considerations:** Keep `FaviconImage(domain, modifier)` source-compatible and preserve current local fallback.

**Performance & Security Considerations:** IO dispatcher only; bounded cache; no DNS lookup, credential, client-IP, or profile-ID transmission in icon URL.

**Design System & UI Adjustments:** Use small, consistently sized list icons with generic fallback and no layout shift.

**Phased PR Plan:** PR 5 only if endpoint behavior and data lifecycle review remain acceptable; otherwise retain current safe best-effort implementation and report the limitation.

## Phased GitHub workflow

1. **Audit and research:** this plan, API inventory, official-source evidence, test-gap list, and clean pre-change baseline.
2. **PR 1 — data and chart correctness:** device labels, diagnostic wording, log filters/reasons/history, GAFAM chart; run API/repository/Compose tests and lint.
3. **PR 2 — capability transparency:** API field inventory and age-setting status; dynamic security fields only after parser/PATCH evidence; run contract and compatibility tests.
4. **PR 3 — performance:** benchmark harness, StrictMode audit, same-device baseline/candidate report; run CI and device measurements.
5. **PR 4 — speed test:** only after the chosen open-source engine and HTTPS test server pass licensing, maintenance, privacy, and security review.
6. **PR 5 — favicon lifecycle:** verify current first-party route, cache/provenance policy, and any retention disclosure before changing runtime behavior.
7. **Review and deployment readiness:** security diff review, full test/lint/build, CI status, BrowserStack or named-device evidence, API matrix, error ledger, and a release-readiness decision. Open PRs but do not imply public release or merge without the project’s approved release gate.

## Research sources consulted

- NextDNS, [API Documentation](https://nextdns.github.io/api/) (beta status, fields, logs query parameters, pagination).
- NextDNS, [test.nextdns.io](https://test.nextdns.io/) (diagnostic response fields; server identifiers are not a city mapping).
- Android Developers, [Compose performance tooling](https://developer.android.com/develop/ui/compose/performance/tooling) and [Compose runtime tracing](https://developer.android.com/develop/ui/compose/tooling/tracing).
- Android Developers, [Benchmark Compose](https://developer.android.com/develop/ui/compose/migrate/compare-metrics) and AndroidX Macrobenchmark frame metrics.
- `bertrandmartel/speed-test-lib`, [README](https://github.com/bertrandmartel/speed-test-lib) and [MIT license](https://github.com/bertrandmartel/speed-test-lib/blob/master/LICENSE.md); endpoint and maintenance suitability still requires review.
- `librespeed/speedtest-android`, [README and LGPL-3.0 terms](https://github.com/librespeed/speedtest-android); requires a configured LibreSpeed server.
- NextDNS favicon host use is evidenced by [NextDNS Help Center discussion](https://help.nextdns.io/t/m1h08qc/using-favicons-nextdns-io-to-request-other-resources) and independent code examples, not by the official API schema.
