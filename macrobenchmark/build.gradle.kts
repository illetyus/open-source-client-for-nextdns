plugins {
  alias(libs.plugins.android.test)
}

android {
  namespace = "com.example.macrobenchmark"
  compileSdk = 36

  defaultConfig {
    minSdk = 28
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testInstrumentationRunnerArguments["androidx.benchmark.fullTracing.enable"] = "true"
  }

  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
  implementation(libs.androidx.benchmark.macro.junit4)
  implementation(libs.androidx.junit)
  implementation(libs.androidx.test.uiautomator)
  implementation(libs.androidx.tracing.perfetto)
  implementation(libs.androidx.tracing.perfetto.binary)
}
