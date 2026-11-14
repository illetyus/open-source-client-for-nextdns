# Physical scroll pipeline validation — 27 October 2026

This validates acquisition on the actual phone. It does not accept application
smoothness, the complete performance plan, or public release readiness.

## Device and artifact identity

Galaxy S20 FE, `SM-G780G`, Android 16 / SDK 36, USB target `RF8T11LKPMZ`.
Production package data and global display/animation settings were not changed.
The separate `.benchmark` application and measurement APK were updated with
`install -r`.

Candidate source: `22ee925c6a4a42707aaced8457916eee98b8eee4`.
The local nondebuggable candidate has R8 disabled for this acquisition.

| Artifact | SHA-256 |
|---|---|
| Candidate application | `4dc6c1c5bb6804ee80a1c940f6c4b8f7e056ad3245266afc3bcadf198fe13005` |
| Shared measurement APK | `a6e30886a565cd3ab348fe18395e40de3efffbf37c137880385c992fb194b443` |
| Baseline-v6 application | `6c459d418e637aff8b32ef10c3a809c86db01b21eac19f4c078426b5260713a1` |

The baseline exports commit `ff8a0b6ec71fe0fbfce95d18a499884599296f7c`,
the same current synthetic fixture sources, two analytics readiness/target tags,
and identical benchmark diagnostic/Perfetto dependencies. Its immutable archive
and overlays are enumerated in the local provenance manifest. It built in
8 minutes 49 seconds. Both application APKs pass the packaged native checksum
preflight against the one shared measurement APK, across all four packaged ABIs.

## Completed physical checks

The earlier diagnostic lifecycle smoke passed one test and exported 660 callback
records without buffer overflow. This validates recording/export functionality.
Reported display mode is not evidence of presented FPS.

After aligning Perfetto client and binary versions, the setup-only pipeline pilot
passed `OK (1 test)` in 456.136 seconds. It produced ten measured iterations and
ten distinct nonempty Perfetto traces, with SHA-256 inventory recorded locally.
CPU frame sample counts by iteration were:
`1391, 1392, 1393, 1397, 1378, 1386, 1381, 1385, 1388, 1399`.

The Macrobenchmark summary reports CPU frame P95 5.796 ms and P99 7.525 ms;
frame overrun P95 -4.082 ms and P99 -2.111 ms. These are single-candidate,
single-scenario sampled summaries, not an A/B improvement or cadence acceptance.

A query of the first raw trace confirms application and SurfaceFlinger frame
timeline records and VSYNC tracks. Its `measureBlock` lasts 21.295 seconds,
including injection overhead; this is not proof of uninterrupted content motion.
The trace reports one `power_rail_empty_packet` import issue, so power evidence
cannot be inferred from this trace. Raw jank events must be restricted to valid
motion windows before they become acceptance metrics.

A fresh setup-only baseline/candidate acquisition was then started with the same
measurement APK hash and display, thermal and battery snapshots before/after each
side. At this report checkpoint it has no reviewed result. Other destinations,
icon workloads, actual 60/120 Hz cadence, streaming latency, memory cycles,
SDK comparison and application-owned Baseline Profile gates remain open.

Both setup-only acquisitions subsequently completed with ten traces each. The
mean per-iteration CPU P95 was 5.792 ms for the baseline and 5.918 ms for the
candidate. These sequential batches started at different battery temperatures
(33.3 °C and 35.7 °C respectively), so the 0.125 ms difference cannot establish
an improvement or meet the paired device acceptance gate. Initial presentation
intervals include both approximately 8.46 ms and 16.92 ms; the cause remains
unresolved and display mode alone cannot establish delivered frame cadence.

Two manual capture attempts do not establish human-scroll performance. The
first overwrote initial trace data; the user later clarified that they did not
scroll during the second capture because the start notification did not reach
them in time. Both are excluded from performance conclusions. A repeat needs a
clear, acknowledged start signal while the phone is connected.

## Separate optimized native test failure

CI 463 (`40535368277`) passed Lint, JVM/Compose checks, both benchmark optimizer
variants and their checksum preflights, release builds, DEX/dependency/privacy
and Snyk gates. The native runner still failed before the 18 required tests:
`kotlin.LazyKt` was absent while `TestDirCalculator` initialized test storage.
This follows the earlier `androidx.tracing.Trace` startup failure; it is not a
physical Perfetto failure and no native PASS is claimed.

The downloaded actual APK pair contains unresolved shared type-reference
candidates, including annotations, platform types and paths outside the selected
native tests. Their count must not be reported as a count of runtime defects.
Applying the app's mapping to tests cannot resurrect an app API already removed
by shrinking.

An isolated probe using the pinned AGP builder's R8 `TraceReferences` and original
monitor/stdlib bytecode emitted exact facade/declaring-member keep roots for
`LazyKt__LazyJVMKt.lazy(Function0)`. Its intentionally incomplete classpath also
reported unresolved dependencies; it is diagnostic output, not production rules
or a clean validation. The next experiment resolves the exact release/test graph
and traces original class/member references before rebuilding. No blanket Kotlin,
Compose or application retention has been added.

The exact release graph export and original test compilation subsequently passed
(33 tasks, 2 minutes 15 seconds). Full reference tracing consumed 1,999 source
classes (including test-only dependencies and 28 local test classes) and 28,405
original target classes. It emitted 455 exact class/member root blocks but exited
with unresolved definitions, including optional legacy Android test APIs, hidden
platform method declarations, a compile-time annotation, and a test bitmap helper
referencing `SuspendToFutureAdapter` absent from resolved concurrent-futures 1.1.0.
This output is not a clean linkage audit or an approved production keep file.
These gaps must be classified/resolved before using the generated rules and
repeating the unchanged native gate.

The local cached artifacts confirm `SuspendToFutureAdapter` is absent from
concurrent-futures-ktx 1.1.0 and present in 1.2.0. AndroidX's
[Concurrent release notes](https://developer.android.com/jetpack/androidx/releases/concurrent)
also identify the adapter as an addition in 1.2.0. This identifies a dependency
incompatibility candidate; it does not prove the currently selected native tests
reach that helper or that changing the version alone fixes their startup.

The cached AndroidX Test Core 1.7.0 POM declares concurrent-futures-ktx 1.2.0.
An explicit app runtime dependency on that version was added to the working
tree. Gradle `dependencyInsight` for the release instrumentation runtime now
resolves 1.2.0 (completed in 12 minutes 2 seconds). This dependency change is
not yet validated by unit tests, Lint or native tests. A combined local run failed
after 29 minutes 45 seconds: `processDebugAndroidTestResources` timed out in
AAPT2 while linking. The timeout does not establish whether this dependency
change caused the failure. CI validation remains required. Paired performance
runs must use matching dependency versions on both sides.

On 30 October the user requested BrowserStack testing after the work is ready.
The authenticated read-only device catalog included Google Pixel 11 Pro XL with
Android 17. No new BrowserStack build was submitted at this checkpoint. BrowserStack
results need to be identified by device and run and do not retroactively prove
the earlier physical Galaxy S20 FE 60/120 Hz acceptance criteria.

Primary references: [Perfetto FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline),
[Perfetto SQL reference](https://perfetto.dev/docs/analysis/stdlib-docs),
[AOSP reference tracing](https://source.android.com/docs/core/perf/r8-optimization).
