# Controlled icon workloads for local scroll comparisons

Benchmark fixtures support `unique` (default), `repeated`, and `warm-repeated`.
Unique preserves a distinct domain per row and the original UTF-8 hexadecimal
favicon routes, including 3x/2x/1x fallbacks. Repeated uses one synthetic icon
key across rows. Warm-repeated loads that same key through the real image loader
before displaying the fixture. No production favicon validation is relaxed.

The application exposes a test tag acknowledging the applied workload. The
measurement harness waits for this acknowledgment, so an older fixture cannot
silently ignore the selected mode. The local runner records the selected mode
in evidence metadata. Baseline exports include all current benchmark-only and
measurement Kotlin sources; production overlays remain limited to two analytics
readiness/target tags.

Build only the application APK from an exported baseline. Use one measurement
APK built from the current tree for both applications, recording and checking
identical `testSha256` values in every paired capture. The archive retains its
original measurement dependency catalog, so an independently built measurement
APK from that archive is not a supported comparison protocol.

Six focused workload tests plus five frame-buffer tests passed (11 total).
The physical diagnostic smoke also passed the default workload acknowledgment.
No workload has yet passed the full paired physical performance acceptance.
Unique means distinct keys, not a permanently cold cache: revisited rows use
the unchanged normal cache behavior.
