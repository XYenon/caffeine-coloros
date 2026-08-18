# Keep Xposed Entry points
-keep class com.caffeine.oxygenos.hook.** { *; }
-keep class com.caffeine.oxygenos.core.** { *; }
-keep class com.caffeine.oxygenos.service.** { *; }
-keep class com.caffeine.oxygenos.provider.** { *; }

# Keep Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
-keep class io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**

# Keep DexKit
-keep class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
