# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okio.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.deepseek.widget.data.** { *; }
-keep class com.google.gson.** { *; }

# Keep widget-related classes
-keep class com.deepseek.widget.** { *; }
