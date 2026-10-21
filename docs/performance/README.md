# Android performance measurements

The repository now contains a release-like Macrobenchmark target and a cold-start/initial-screen scroll journey. The benchmark also enables Compose composition tracing and writes Macrobenchmark JSON and Perfetto traces when run on a supported connected Android device.

Build the harness with `./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark`. On a physical Android 14+ device, run `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest` and retain its JSON and trace outputs. Record device model, Android build, app commit, battery/power conditions, theme, locale, and the benchmark command with each result.

No physical-device baseline or before/after comparison is recorded yet. Emulator timings must be labeled as emulator-only and must not be presented as representative of a user's phone. The current journey covers cold launch and the initial screen; authenticated home, logs, and analytics journeys require a test account/profile fixture before they can be measured reproducibly.
