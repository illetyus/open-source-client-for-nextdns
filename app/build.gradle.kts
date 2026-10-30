plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  id("jacoco")
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.nextdns.mgrqvt"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // The CI runner has no private keystore. Assemble an unsigned release there,
  // but never silently fall back to unsigned signing when release credentials
  // were explicitly supplied.
  val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
  val releaseStorePassword = System.getenv("STORE_PASSWORD")
  val releaseKeyPassword = System.getenv("KEY_PASSWORD")
  val releaseKeyAlias = System.getenv("KEY_ALIAS") ?: "upload"
  val signingInputs = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyPassword)
  val signingRequested = signingInputs.any { !it.isNullOrBlank() }
  val signingComplete = signingInputs.all { !it.isNullOrBlank() }
  if (signingRequested) {
    require(signingComplete && file(releaseKeystorePath!!).isFile) {
      "Release signing configuration is incomplete or keystore file is missing"
    }
  }
  signingConfigs {
    if (signingRequested) {
      create("release") {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // Compare optimizer-only APKs before changing the default review payload.
      // Promotion requires the minified runtime and physical scroll gates.
      isMinifyEnabled = providers.gradleProperty("scrollMinify").orNull == "true"
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      testProguardFiles("test-proguard-rules.pro")
      // Release packaging is tested unsigned when CI has no signing secrets.
      if (signingRequested) signingConfig = signingConfigs.getByName("release")
    }
    create("benchmark") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = false
      isProfileable = true
      applicationIdSuffix = ".benchmark"
      matchingFallbacks += listOf("release")
    }
    debug {
      enableUnitTestCoverage = true
      // Use Android Gradle Plugin's standard auto-generated debug keystore.
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  // A disposable CI emulator can instrument the release payload. The default
  // device-test variant remains debug for BrowserStack's existing workflow.
  if (providers.gradleProperty("releaseRuntimeAudit").orNull == "true") {
    testBuildType = "release"
  }
  if (providers.gradleProperty("benchmarkUnitTests").orNull == "true") {
    require(providers.gradleProperty("releaseRuntimeAudit").orNull != "true") {
      "Benchmark unit checks and release instrumentation use separate Gradle invocations"
    }
    testBuildType = "benchmark"
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

tasks.withType<Test>().configureEach {
  extensions.configure<org.gradle.testing.jacoco.plugins.JacocoTaskExtension> {
    // Robolectric loads Android application classes without a source location.
    isIncludeNoLocationClasses = true
    includes = listOf("com.example.*")
    excludes = listOf("jdk.internal.*")
  }
}

// AGP 9.1.1 runs L8 independently for the app and its instrumentation APK.
// Instrumentation loads its classes first: a separately renamed j$.time.ZoneId
// can therefore shadow the app's ZoneOffset with an incompatible superclass.
// Separate closed-world L8 optimizations cannot safely share a class loader:
// applymapping alone still permits different hierarchies (e.g. Clock/SystemClock).
// Retain stable backport definitions, matching AGP's non-minified L8 settings,
// and keep the app's desugared API in the test copy. App R8 remains optimized;
// only backport-library optimization/renaming is disabled, not dead-code removal.
// This version-pinned internal-task workaround must be reviewed with AGP upgrades.
tasks.withType<com.android.build.gradle.internal.tasks.L8DexDesugarLibTask>().configureEach {
  val stableBackportTasks = setOf("l8DexDesugarLibRelease", "l8DexDesugarLibBenchmark",
    "l8DexDesugarLibReleaseAndroidTest", "l8DexDesugarLibBenchmarkAndroidTest")
  if (name in stableBackportTasks && providers.gradleProperty("scrollMinify").orNull == "true") {
    keepRulesConfigurations.addAll("-dontobfuscate", "-dontoptimize")
  }
  if (name == "l8DexDesugarLibReleaseAndroidTest" && providers.gradleProperty("scrollMinify").orNull == "true") {
    val appL8 = tasks.named<com.android.build.gradle.internal.tasks.L8DexDesugarLibTask>(
      "l8DexDesugarLibRelease"
    )
    dependsOn(appL8)
    keepRulesConfigurations.add(appL8.flatMap { it.keepRules }.map {
      "-include \"${it.asFile.invariantSeparatorsPath}\""
    })
  }
}

tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")
  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(false)
  }
  val mainSrc = "${project.projectDir}/src/main/java"
  sourceDirectories.setFrom(files(mainSrc))
  // AGP 9 built-in Kotlin no longer writes classes to tmp/kotlin-classes.
  // Use compiler task outputs so coverage follows the actual build layout.
  classDirectories.setFrom(
    listOf("compileDebugKotlin", "compileDebugJavaWithJavac").map { taskName ->
      tasks.named(taskName).map { compiler ->
        compiler.outputs.files.asFileTree.matching {
          include("**/*.class")
          exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*", "**/*Test*.*")
        }
      }
    }
  )
  executionData.setFrom(fileTree(layout.buildDirectory.get()) {
    include(
      "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
      "jacoco/testDebugUnitTest.exec"
    )
  })
}

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // AndroidX Test Core 1.7.0 calls SuspendToFutureAdapter, introduced in
  // 1.2.0. Instrumentation uses the app's shared runtime copy, so align it.
  implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockwebserver)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.mockwebserver)
  androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.10.0")
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  add("benchmarkImplementation", libs.androidx.compose.runtime.tracing)
  // Match the binary shipped by Macrobenchmark; runtime-tracing alone selects 1.0.0.
  add("benchmarkImplementation", libs.androidx.tracing.perfetto)
  add("benchmarkImplementation", "com.squareup.okhttp3:okhttp-tls:4.10.0")
  add("benchmarkImplementation", "androidx.metrics:metrics-performance:1.0.0")
  "ksp"(libs.moshi.kotlin.codegen)
}
