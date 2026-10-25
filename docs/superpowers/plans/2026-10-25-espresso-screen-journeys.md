# Espresso Screen Journeys Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement the approved screen-test scope inline. Track each verification below.

**Goal:** Exercise every main destination and representative user interactions on a real Pixel, then assess these results together with the earlier runs.

**Architecture:** Launch the production activity for the guest journey. For authenticated journeys, render the production HomeScreen with the production ViewModel and repository using a device-local TLS fixture and isolated preferences. Test data and API keys are synthetic. Route every fixture client request to localhost; disable background account work.

**Tech Stack:** AndroidJUnit4, Espresso/Compose UI Test, ActivityScenario, MockWebServer, BrowserStack Espresso.

**Spec:** The approved conversation scope: onboarding, setup, security, privacy, parental controls, denylist, allowlist, analytics, logs and settings. Existing notification and HTTP findings remain open for the aggregate assessment.

## Global Constraints

- Preserve app behavior, service API contracts, package identity, legal DRAFT status and existing security assertions.
- Add only UI semantics needed to identify existing controls and destinations reliably.
- Use the current tested source, 2bd6e3c, on a separate test branch.
- No real NextDNS credentials or account mutations; no Supabase dependency is required by this app.
- Report real-device results separately from compilation and submission.

## Review Focus

- Compact, rail and drawer navigation must reach the same nine destinations.
- Activity recreation and rotation must preserve a usable guest session.
- Domain validation must prevent malformed mutations and valid inputs must reach the API.
- Logs and analytics filters must change actual repository requests, not just labels.
- A failed settings mutation must leave the last confirmed value intact and allow a later retry.

## Task 1: Stable screen semantics and guest navigation

- [ ] Add literal screen tags at existing HomeScreen branches and tags to existing list/filter controls.
- [ ] Add real-activity guest journeys covering all nine tabs, legal readers, rotation and recreation.
- [ ] Compile the instrumentation APK. Record any environment blocker without changing security settings.

## Task 2: Connected interaction journeys

- [ ] Add an isolated TLS fixture using the existing API and repository interfaces.
- [ ] Test all destinations, a security toggle with rejected mutation and retry, allow/deny list input, log history/status/search/device filters, analytics device/time filters and settings legal readers.
- [ ] Compile, then run the new classes on Pixel 11 Pro XL / Android 17. Retain assertions when a product defect is found.

## Task 3: Evidence and aggregate assessment

- [ ] Save APK hashes, source commit, launch payload, build/session IDs and JUnit results.
- [ ] Review the test diff for misleading assertions, fixture leakage and unintended production behavior changes.
- [ ] Report new coverage together with the existing 16/18 Pixel result and two unresolved findings. Do not infer full real-account or release readiness from fixture UI coverage.
