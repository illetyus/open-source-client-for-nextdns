# Local 60/120 Hz scroll smoothness implementation plan

Approved in chat on 26 October 2026. Source baseline: `e395981af203e1b44adcf9b69ef05dfa4c7d71f7` (current review UI; PRs #35/#36 remain dependencies). Execute inline with `superpowers:executing-plans`; maintain `.superpowers/sdd/2026-10-26-local-scroll-smoothness/progress.md`.

## Contract

- No BrowserStack. Physical Galaxy S20 FE at actual 60 and 120 Hz is the performance acceptance device. Emulator/CI results prove functionality only.
- Preserve appearance, animation timing, icon quality, every service operation, five languages, accessibility, profile/session isolation and existing data. Never uninstall the production package to obtain a benchmark.
- Preserve API URLs/JSON names, production application ID, 1,000 retained logs, cursor/event identity, filter generations, polling cadence and legal DRAFT status.
- Archive the same-harness baseline/candidate APK hashes, commits, device/OS/display/thermal conditions, raw JSON and Perfetto traces. No private domains, addresses or credentials in performance metadata.
- Keep changes as independently reviewable commits. A candidate optimization is not a proven improvement until a physical paired comparison demonstrates its benefit without regression.

## Task 1 — measurement harness and baseline

- Keep the isolated `.benchmark` application and synthetic HTTPS fixture.
- Separate startup/navigation/data preparation from steady scroll measurement; add fixed slow drags/fast flings, both directions, and stable screen/list targets.
- Exercise all nine destinations; critical cases include 200/1,000 logs, warm/cold/repeated/unique favicon workloads, large device/catalog sections and live 5/s plus 50/s bursts.
- Inject the real favicon loader with loopback-only synthetic image responses in the benchmark variant. `.invalid` must remain forbidden by the production URL resolver.
- Add diagnostic-only JankStats 1.0.0, screen/interaction/cache labels, actual refresh cadence sampling and local artifact capture. Fast callbacks; no telemetry or production tracing dependency.
- Read OS/device identity and refresh changes across each active motion window. A 120 Hz panel producing 60 fps must not pass as smooth 120 Hz.
- Three warmups, ten matched A/B measurements and at least 30 seconds aggregate moving content per critical scenario. Exclude fixture waits from the scroll interval, not unfavorable frames.

## Task 2 — optimized build with reflection compatibility

- Compare non-debuggable R8-off versus R8-on at the same source revision before attributing a source regression. Keep resource shrinking off initially.
- Inventory reflected Moshi API and persisted models; preserve their field names/constructors and Retrofit metadata with explicit keep rules. Preserve old stored JSON without a migration.
- Exercise serialization and actual minified payload behavior. Audit mapping and final DEX for test/benchmark code; never weaken evidence checks merely because names are obfuscated.

## Task 3 — independently measured source candidates

- Cache map geometry and viewport paths; cull offscreen seam copies including stroke bounds. Preserve all points, holes, seam hit-testing, country selection and accessibility. Selection must not rebuild geometry.
- Cache chart segments/maxima while preserving null gaps, empty/single points and selection.
- Reuse localized resources through configuration-aware snapshots; invalidate on app/system locale and configuration changes. Retained ViewModels/workers remain current. No shared mutable NumberFormat across threads.
- Parse row timestamps once, retaining current relative-age and locale update behavior.
- Coalesce same-domain favicon requests with cancellation ownership: cancelling one consumer must not cancel the others; cancel underlying work when the last leaves. Preserve 4 MiB cache, 192 px cap, High filtering, HTTPS host/redirect/MIME/byte/pixel limits, fallback order and 8-second budget. No new disk cache or negative-result retry policy.
- Add stable keys/content types and narrower panel parameters. Only virtualize oversized card row groups if traces identify that cost; preserve shared card chrome, semantics and outer scrolling.
- Only change SSE publication if measured Main work warrants it. Ordered lossless accumulation, indexed IDs and at most one rendered snapshot per display frame must preserve every accepted event, cursor commit, generation guard, refresh merge, retention and scroll anchor.

## Task 4 — separate SDK comparison

- Only after existing-SDK candidates: Compose BOM 2026.09.00 (UI/Foundation/Runtime 1.12.1, Material3 1.4.0), compileSdk 37 in app/test modules. Keep targetSdk 36/minSdk 24, AGP 9.1.1, Gradle 9.3.1, Kotlin 2.2.10 and JDK 17.
- Actual Google Maven AAR metadata requires compileSdk 37/minAGP 9.1.0/minSdk 23; AGP 9.1.1's current compatibility table supports API 37. Do not use the 1.13 beta SDK 37.1 path.
- Runtime tracing 1.12.1 is benchmark-only. Verify strong skipping using compiler reports; do not falsely annotate mutable graphs stable.
- Migrate placement animation to animateItem without adding fades. Review every visual/touch delta; never bulk-update golden images to accept a regression.
- Test default prefetch first. Keep a custom cache window only with repeatable >=10% P95 CPU or >=20% missed-frame improvement and <=10% extra peak memory. Background text measurement needs trace attribution and a bounded shared executor. No production refresh-rate override.

## Task 5 — app-owned Baseline Profile

- Baseline Profile plugin 1.5.0, current Macrobenchmark 1.5.0/ProfileInstaller 1.4.0, authenticated production scroll paths exercised by the isolated fixture.
- Generate locally; a managed emulator may generate profiles, never certify physical-device smoothness. Exclude fixture-only rules/code from the release.
- Verify embedded application profile and compare CompilationMode.None versus Partial with profile required. Full compilation alone is not user evidence. Startup Profile is deferred.

## Task 6 — acceptance and delivery

- Warm scrolling: missed presentation opportunities <=1%, frameOverrun P95 <=0 ms and P99 <=one actual refresh interval (16.67/8.33 ms).
- Cold icons/live data: missed opportunities <=2%; no unexplained application frame >100 ms during steady motion.
- Streaming latency is measured from fixture event emission through processing/publication/presentation, not only after parsing: normal P95 <=50 ms; 50/s burst P95 <=100 ms, P99 <=250 ms; backlog drains within 250 ms and does not grow indefinitely. Report any increase over baseline.
- Report paired distributions/confidence. Practical P95 CPU regression threshold is max(5%,0.5 ms); at most ten extra pairs if inconclusive, then leave the result inconclusive. Predeclare thermal/display validity, retain outliers, and treat repeatable candidate-induced heating as a regression.
- Peak memory <=10% over baseline, bounded caches and no monotonic growth over 20 screen cycles.
- Existing API/mutation rollback, session/profile, locale, log history/SSE, map, icon security, legal, a11y tests remain mandatory. Add focused behavioral regressions, not source-text mirrors. Run Lint/build/unit/Compose/minified runtime/security gates without BrowserStack.
- Document actual results and defect ledger. Deliver optimized non-debuggable review APK with SHA-256 to the existing CrossDevice Download folder only after the local gate passes. Update an installed package only with a compatible signing certificate; otherwise use an isolated review package without deleting user data.
- No connected suitable physical device means no smoothness PASS claim and no final acceptance; continue independent implementation/test work and retain the open device gate.
