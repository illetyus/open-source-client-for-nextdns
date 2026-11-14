# NextDNS Security API Fields Implementation Plan

> **For agentic workers:** This task is authorized in the existing PR #35 workflow. Follow the task steps in order and keep the reviewable PR open.

**Goal:** Turn the 11 observed NextDNS Security API booleans into localized, synchronized toggles while retaining read-only treatment only for genuinely unknown fields.

**Architecture:** Type the fields in the Security API DTO, PATCH request, local settings model, and mutation enum. Reuse the existing profile-scoped `PATCH /profiles/{profileId}/security` flow, sending only the changed boolean and verifying it after refresh. Render each as a normal Security toggle and keep the bounded unknown-field reader for unrecognized keys.

**Tech Stack:** Kotlin, Moshi, Retrofit, Jetpack Compose, MockWebServer, JUnit.

**Spec:** User supplied account screenshot and prior NextDNS API field implementation request.

## Global Constraints

- Use the observed camel-case JSON names exactly: `dataDropServices`, `decentralizedWebGateways`, `dnsDataExfiltration`, `dnsPayloadDelivery`, `fastFluxNetworks`, `freeHostingDomains`, `highRiskTlds`, `newlyActiveDomains`, `residentialHosting`, `tunnelingEndpoints`, `untrustedCertificates`.
- Keep unknown Security fields bounded, non-persisted, and excluded from PATCH requests.
- Keep writes profile-scoped and PATCH only the selected field.
- Localize titles and descriptions in English, Turkish, German, French, and Spanish.
- Never change `settings.bav`, the API host, Android package ID, or unrelated profile behavior.

## Review Focus

- A missing field in an older API response preserves its previous local value.
- Each toggle serializes one camel-case key and does not send null or other Security values.
- The 11 recognized values leave the read-only unknown-fields section; unrelated future keys remain there.
- Each of the 11 setting changes participates in configuration snapshot hashing.
- A rejected server mutation does not persist an optimistic value.

---

### Task 1: API contract and state mapping

**Files:** `NextDnsApiService.kt`, `SecurityDtoJsonAdapterFactory.kt`, `NextDnsModels.kt`, `MutationModels.kt`, `NextDnsRepository.kt`, `ConfigSnapshotHasher.kt` and their focused tests.

- [x] Add contract tests that parse the exact 11 fields as typed booleans and serialize a PATCH request containing only a selected field.
- [ ] Run the focused contract tests and confirm the new-key recognition and serialization assertions fail on the current implementation. The attempt was blocked before Gradle could start because this environment denies Gradle's loopback daemon connection.
- [x] Add nullable DTO and PATCH properties, add local defaults, whitelist the keys in discovery, and extend SecurityFlag mutation and hash coverage.
- [x] Add repository mutation regressions for isolated PATCH, refresh/persistence, and preserving cached values when an older response omits a field; JVM execution still needs CI because local Gradle cannot start.

### Task 2: Security controls and localization

**Files:** `SecurityScreen.kt`, `NextDnsViewModel.kt`, five `strings.xml` resources, `AccountSettingsFlowTest.kt`.

- [x] Add a UI regression proving recognized keys render as actionable toggles and are absent from the read-only discovery group.
- [x] Add toggle labels/descriptions in all five locales, wire each toggle to the existing `toggleSecurityFeature` path, and keep unknown fields read-only.
- [x] Run the localization verifier; the Compose test is queued for PR CI.

### Task 3: API evidence and release verification

**Files:** `NEXTDNS_API_CAPABILITY_MATRIX_TR.md`, `HATA_DEFTERI_TR.md`.

- [x] Document the Security object `GET/PATCH` route, the exact observed fields, the source limits for the official schema, and the fact that live account mutation was not run.
- [ ] Run the full Android CI quality gates and Codex Security diff scan for the updated PR head; update the open PR with the results.
- [ ] Keep PR #35 open for review; do not merge or publish.
