# Optional local frame diagnostics

The primary A/B capture remains `SteadyScrollBenchmark`: 13 scenarios, ten
iterations each, raw frame arrays and 130 separate Perfetto traces. The normal
runner does not enable the diagnostic observer. Baseline and candidate must use
the same instrumentation APK hash and matching diagnostic settings.

`run_local_scroll.ps1 -Diagnostics` additionally enables JankStats 1.0.0 in the
isolated `.benchmark` app. It records frame start, UI duration, the library's
jank flag, current display mode/rate and cached thermal status. The callback
copies six primitives into a synchronized, bounded 30,000-row buffer. It performs
no file I/O, text formatting, view traversal or network operation. Overflow is
reported explicitly and never replaces an unfavorable early frame with a later
one. Thermal status is unknown on API 28; the listener is guarded on API 29+.

After activity stop, detached arrays are exported on a dedicated worker to
`Android/media/com.aistudio.nextdns.mgrqvt.benchmark/scroll-diagnostics/<capture>/<scenario>/`.
Only completed `.json` files count as committed; interrupted `.tmp` exports do
not. The instrumentation waits for diagnostic export only after
`measureRepeated`, and the runner pulls the directory into that capture's local
evidence folder. Labels contain only fixed scenario names and the runner's
timestamp, with a restricted filename alphabet. These fixtures use synthetic
data. No diagnostic recorder, JankStats dependency or telemetry is added to the
production build type.

Activity captures include preparation, scrolls and possibly compilation warmups.
Their file-count check does not establish a one-to-one match to measured
iterations. They help locate work; they are not the frame acceptance dataset.
The core trace validator still separately requires every measured iteration.
The new recorder's device lifecycle/export behavior has not yet been exercised
on a connected Android device.

JankStats reuses its callback `FrameData`; retaining that object would corrupt
observations. Its UI duration excludes RenderThread work, and its jank heuristic
is not the agreed missed-presentation-opportunity metric. A display mode of
120 Hz does not prove 120 presented frames per second. The JSON explicitly marks
these limits. Actual presentation cadence, motion windows and CPU/GPU attribution
must come from the matching physical-device Perfetto traces. Run diagnostics
separately from the primary timing and memory comparisons so the observer and
export do not silently change the workload.

Sources: [JankStats API and lifecycle](https://developer.android.com/reference/androidx/metrics/performance/JankStats),
[reusable FrameData and UI duration](https://developer.android.com/reference/androidx/metrics/performance/FrameData).

Validation at implementation: five pure Kotlin/JUnit tests passed, including
concurrent callback bounds, snapshot isolation and overflow retention. The same
five tests passed through the Android benchmark Gradle variant; benchmark app
Kotlin and measurement APK compilation also succeeded (12 minutes 17 seconds).
Final display-removal/API-guard changes and the new smoke test compiled in a
subsequent check; benchmark Lint completed with zero errors and 165 warnings
(6 minutes 39 seconds). No warnings point to the new frame recorder or fixture
changes. These checks do not establish the recorder's Android lifecycle/export
behavior or physical performance acceptance.

The CI release audit now runs a separate `DiagnosticLifecycleSmokeTest` after
the unchanged 18-test native release verifier. It starts the real isolated
fixture, scrolls, sends it home, checks committed JSON and actual frame callbacks,
and then stops only the fixture package. It uses no Macrobenchmark rule and
makes no emulator FPS claim. Its execution is pending. A dedicated logcat is
saved even if the smoke test fails. A fixed emulator port and `ANDROID_SERIAL`
confine the CI audit's ADB commands to that emulator.

The CI recipe also archives `candidate-r8-off.apk` and `candidate-r8-on.apk` from
the same checkout, their SHA-256 sums, and the optimized benchmark mapping. The
smoke test uses the optimized benchmark APK. Availability of these artifacts is
not a measured R8 performance benefit; matching physical comparisons remain open.
