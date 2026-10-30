# Host-compiler annotations pulled in by instrumentation's test dependencies.
# Neither Java's annotation-processing Modifier nor Animal Sniffer is an Android
# runtime API. These rules apply to the test APK only, never the release payload.
-dontwarn javax.lang.model.element.Modifier
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
