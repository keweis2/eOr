-keep class com.gamelaunch.frontend.data.network.dto.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Gson: under R8 full mode (AGP 8 default) -keepattributes Signature alone does NOT stop the type
# argument being stripped from anonymous TypeToken subclasses, which throws at runtime
# ("TypeToken must be created with a type argument"). These are Gson's official R8 rules and are
# needed by the TypeToken<Map<String,String>>(){} usages in EmulatorRepositoryImpl and Converters.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
