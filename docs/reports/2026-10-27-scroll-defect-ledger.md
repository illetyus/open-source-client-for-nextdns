# Scroll candidate defect ledger

This ledger belongs to PR #37 and the approved local scroll plan. None of these
entries grants physical-device performance acceptance or public-release approval.

## SCROLL-01: minified instrumentation desugaring collision

**Reproduced; fix under verification.** CI run `40362123060`, head `977f336`,
completed its build, JVM, Lint, DEX and dependency checks, then crashed before
running the 18 required native tests. Both permission phases returned
`INSTRUMENTATION_RESULT: shortMsg=Process crashed.`

Logcat reports a `VerifyError`: `j$.time.ZoneOffset` is not assignable to
`j$.time.ZoneId` in the instrumentation process. The actual signed artifact pair
contains these different definitions:

| Artifact | Class | Direct superclass |
|---|---|---|
| application | `j$.time.ZoneOffset` | `j$.time.ZoneId` |
| instrumentation | `j$.time.ZoneOffset` | `j$.time.a0` |

The independently optimized test copy shadows the application's definition in
the shared class loader. The same pair also loses `Duration` to
`TemporalAmount` assignability. This establishes a test packaging compatibility
failure; a standalone application launch has not established a production crash.

The first candidate gave release-test L8 the application's generated keep rules
and mapping. CI `40485271181` built both APKs but the preflight correctly rejected
another divergence: `j$.time.a` lost `j$.time.b` assignability. The application's
mapping identifies `Clock$SystemClock -> a` and `Clock -> b`. Sharing mapping
alone is insufficient for two independently optimized closed-world libraries.

The second candidate retains stable backport types in both application and test
L8 (`-dontobfuscate`, `-dontoptimize`), the same settings AGP 9.1.1 uses for its
non-minified backport library. The test copy additionally retains the app's
generated backport API keep rules. Application R8 remains enabled and optimized;
backport dead-code removal remains enabled. This trades potential backport-only
optimization/size gains for a consistent shared runtime ABI; the tradeoff still
needs native execution and physical performance comparison. The local real APK
pair built successfully in 26 minutes 3 seconds; the unchanged preflight passed
for 187 shared classes. This is type-hierarchy evidence, not a member ABI or
native runtime PASS.
AGP 9.1.1's internal task API is version-specific and must be reconsidered on AGP
upgrades. A new DEX-pair preflight checks inherited type assignability before
creating the emulator. This does not validate every member ABI or replace native
execution. The old signed pair is correctly rejected by the new preflight.

Primary implementation reference: [Android build tools: L8 mapping
handling](https://android.googlesource.com/platform/tools/base/+/98389f491c2db62d780ebc04e555a58d471fcb46%5E%21/).
This source explains mapping output; it is not evidence that an upstream fix for
this collision is present in the pinned version. Neither failed CI run is a
native compatibility PASS; the second stopped before emulator creation.

## SCROLL-02: Unicode URL memo aliases rejected input

**Fixed; focused regressions passed.** The new URL memo used a
lowercased raw hostname as its key, before applying the original IDN validator.
IDNA2003 accepts `\u00df.example.org`, whereas it rejects
`\u1e9e.example.org`; lowercasing makes their memo keys equal. Loading the valid
input first can therefore give the rejected input an icon.

The MockWebServer regression uses the real production URL validator, fetches one
valid image, and requires the rejected input to return no image and issue no
additional request. Before the fix it failed because the rejected input returned
a `Bitmap` (one test, 12 minutes 22 seconds). The fix retains the original trimmed input in the
resolution memo. Validated canonical URLs still identify shared HTTP jobs and
decoded images. The fixed HTTPS host and existing network guards remain in force.
The complete favicon network/URL/domain-mapping suites plus timestamp tests passed:
28 tests, zero failures/errors/skips, 8 minutes 20 seconds. Shared requests,
individual/last-consumer cancellation, retry and image guards remain covered.

## SCROLL-03: repeated log timestamp parsing

**Implemented; focused behavior checks passed.** Four characterization tests
passed on the original parser. After extraction and row-level memoization of the
parsed instant, five tests passed, including recomposition of the retained row
after an English-to-Turkish language change. Age and localized units are still
computed from the current clock and current language. Unknown text and existing
timestamp formats retain their behavior. This is a source optimization candidate;
its frame-time benefit has not been measured on the physical phone.
