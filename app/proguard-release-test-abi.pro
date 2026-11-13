# The release instrumentation APK resolves these shared dependencies through
# the target APK's class loader. Keep the exact facades and app classes that the
# AndroidX runner and native tests call by their compiled names. This file is
# attached only to the disposable releaseRuntimeAudit build, never production.
-keep class androidx.tracing.Trace { public *; }
-keep class kotlin.LazyKt { *; }
-keep class kotlin.jvm.internal.Reflection { *; }
-keep interface kotlin.reflect.KClass { *; }
-keep class kotlin.io.FilesKt { *; }
-keep class kotlin.text.StringsKt {
  public static boolean contains$default(java.lang.CharSequence,java.lang.CharSequence,boolean,int,java.lang.Object);
}
-keep class kotlinx.coroutines.BuildersKt { *; }
-keep class com.example.data.local.NextDnsPreferences { *; }
-keep class com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory { *; }
