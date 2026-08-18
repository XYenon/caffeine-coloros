# Keep libxposed entry points and injected module classes
-keep class bid.xyenon.caffeine.coloros.hook.** { *; }
-keep class bid.xyenon.caffeine.coloros.core.** { *; }
-keep class bid.xyenon.caffeine.coloros.service.** { *; }
-keep class bid.xyenon.caffeine.coloros.provider.** { *; }
-keep class bid.xyenon.caffeine.coloros.ui.** { *; }
# Keep libxposed API
-keep class io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

# Keep DexKit
-keep class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
