pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    if (providers.environmentVariable("GITHUB_ACTIONS").orNull == "true") {
      // Hosted runners repeatedly hit Central's download limit on this large test-only JAR.
      maven("https://maven-central.storage-download.googleapis.com/maven2/") {
        content { includeVersion("org.robolectric", "nativeruntime-dist-compat", "1.0.18") }
      }
    }
    mavenCentral()
  }
}

rootProject.name = "open-source-client-for-nextdns"

include(":app")
include(":macrobenchmark")
