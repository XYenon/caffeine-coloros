# Keep Xposed Entry points & Application classes
-keep class bid.xyenon.caffeine.coloros.hook.** { *; }
-keep class bid.xyenon.caffeine.coloros.core.** { *; }
-keep class bid.xyenon.caffeine.coloros.service.** { *; }
-keep class bid.xyenon.caffeine.coloros.provider.** { *; }
-keep class bid.xyenon.caffeine.coloros.ui.** { *; }
-keepclassmembers class bid.xyenon.caffeine.coloros.ui.MainActivity {
    public boolean isLSPosedHookActive();
}

# Keep Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
-keep class io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-adaptresourcefilecontents assets/xposed_init

# Keep DexKit
-keep class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
