# Keep JNI callback interface (called from native code)
-keep class io.github.jqssun.airplay.bridge.RaopCallbackHandler { *; }
-keep class * implements io.github.jqssun.airplay.bridge.RaopCallbackHandler { *; }
-keep class io.github.jqssun.airplay.bridge.LogListener { *; }
-keep class * implements io.github.jqssun.airplay.bridge.LogListener { *; }

# Keep NativeBridge native methods
-keep class io.github.jqssun.airplay.bridge.NativeBridge { *; }

-keep class com.corvofeng.fakeatv.AtvNative** { *; }
-keep class * implements com.corvofeng.fakeatv.AtvNative$Callback { *; }
-keep class com.corvofeng.fakeatv.PersistentInput { *; }
