# The instrumentation APK and app APK share classes through one runtime class
# loader. Native tests call these dependency APIs directly, so preserve their
# names and signatures in the disposable runtime-audit payload. This file is
# attached only when releaseRuntimeAudit=true; the production release keeps
# its normal R8 shrinking and obfuscation rules.
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

# Native runtime tests call application data, localization and view-model APIs
# directly. Compose screens remain minified except for the two tested helpers.
-keep class com.example.data.** { *; }
-keep class com.example.i18n.** { *; }
-keep class com.example.ui.viewmodel.** { *; }
-keep class com.example.ui.screens.LogsScreenKt { *; }
-keep class com.example.ui.screens.WorldMapChartKt { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.NextDnsApp { *; }
-keep class com.example.BuildConfig { *; }
-keep class com.example.R$* { *; }
